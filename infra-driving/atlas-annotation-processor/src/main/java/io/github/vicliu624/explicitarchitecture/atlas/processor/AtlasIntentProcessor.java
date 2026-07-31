package io.github.vicliu624.explicitarchitecture.atlas.processor;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasIntent;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 将任何带 {@link AtlasIntent} 元注解的源码注解写为类型保持的 Intent Manifest。
 *
 * <p>处理器只提取开发者明确声明的意图，不猜测 Spring/Axon 等技术事实；新 Atlas 注解只需添加元注解，
 * 无需修改本处理器的硬编码类型列表。</p>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
public final class AtlasIntentProcessor extends AbstractProcessor {

    private static final String MANIFEST_PATH = "META-INF/explicit-architecture-atlas/atlas-intent.json";

    private final Set<IntentDeclaration> declarations = new LinkedHashSet<>();
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element rootElement : roundEnvironment.getRootElements()) {
            collectDeclarations(rootElement);
        }
        if (roundEnvironment.processingOver() && !written) {
            writeManifest();
            written = true;
        }
        return false;
    }

    private void collectDeclarations(Element element) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            Optional<String> assetKind = atlasIntentKind(annotation);
            if (assetKind.isPresent()) {
                declarations.add(toDeclaration(element, annotation, assetKind.orElseThrow()));
            }
        }
        for (Element enclosed : element.getEnclosedElements()) {
            collectDeclarations(enclosed);
        }
    }

    private Optional<String> atlasIntentKind(AnnotationMirror usedAnnotation) {
        Element annotationElement = usedAnnotation.getAnnotationType().asElement();
        for (AnnotationMirror metaAnnotation : annotationElement.getAnnotationMirrors()) {
            if (!metaAnnotation.getAnnotationType().asElement().toString().equals(AtlasIntent.class.getName())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(metaAnnotation).entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("assetKind")) {
                    Object value = entry.getValue().getValue();
                    if (value instanceof VariableElement enumConstant) {
                        return Optional.of(enumConstant.getSimpleName().toString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private IntentDeclaration toDeclaration(Element element, AnnotationMirror annotation, String assetKind) {
        TypeElement annotationType = (TypeElement) annotation.getAnnotationType().asElement();
        Map<String, String> attributes = annotationAttributes(annotation);
        IntentTarget target = targetOf(element);
        String explicitId = stringAttribute(annotation, "id").orElse("");
        String identitySeed = explicitId.isBlank()
                ? annotationType.getQualifiedName() + ":" + target.binaryName() + ":" + target.memberDescriptor()
                : explicitId;
        return new IntentDeclaration(
                stableId("intent:" + annotationType.getSimpleName() + ":" + identitySeed),
                annotationType.getQualifiedName().toString(),
                assetKind,
                target,
                attributes);
    }

    private Map<String, String> annotationAttributes(AnnotationMirror annotation) {
        Map<String, String> attributes = new TreeMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).entrySet()) {
            attributes.put(entry.getKey().getSimpleName().toString(), jsonValue(entry.getValue()));
        }
        return Map.copyOf(attributes);
    }

    private Optional<String> stringAttribute(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name) && entry.getValue().getValue() instanceof String value) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private IntentTarget targetOf(Element element) {
        String binaryName = binaryNameOf(element);
        String memberDescriptor = switch (element.getKind()) {
            case METHOD, CONSTRUCTOR -> methodDescriptor((ExecutableElement) element);
            case FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> element.getSimpleName().toString();
            default -> "";
        };
        return new IntentTarget(element.getKind().name(), binaryName, memberDescriptor);
    }

    private String binaryNameOf(Element element) {
        Element cursor = element;
        while (cursor != null && !(cursor instanceof TypeElement) && !(cursor instanceof PackageElement)) {
            cursor = cursor.getEnclosingElement();
        }
        if (cursor instanceof TypeElement typeElement) {
            return processingEnv.getElementUtils().getBinaryName(typeElement).toString();
        }
        if (cursor instanceof PackageElement packageElement) {
            return packageElement.getQualifiedName().toString();
        }
        return element.toString();
    }

    private static String methodDescriptor(ExecutableElement method) {
        List<String> parameterTypes = method.getParameters().stream()
                .map(VariableElement::asType)
                .map(TypeMirror::toString)
                .toList();
        return method.getSimpleName() + "(" + String.join(",", parameterTypes) + ")";
    }

    private void writeManifest() {
        List<IntentDeclaration> sorted = declarations.stream()
                .sorted(Comparator.comparing(IntentDeclaration::id))
                .toList();
        Filer filer = processingEnv.getFiler();
        try {
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", MANIFEST_PATH);
            try (Writer writer = resource.openWriter()) {
                writer.write("{\n  \"schemaVersion\": \"1.0\",\n  \"declarations\": [");
                for (int index = 0; index < sorted.size(); index++) {
                    IntentDeclaration declaration = sorted.get(index);
                    writer.write(index == 0 ? "\n" : ",\n");
                    writer.write("    {\"id\": ");
                    writer.write(jsonString(declaration.id()));
                    writer.write(", \"annotationType\": ");
                    writer.write(jsonString(declaration.annotationType()));
                    writer.write(", \"assetKind\": ");
                    writer.write(jsonString(declaration.assetKind()));
                    writer.write(", \"target\": {\"kind\": ");
                    writer.write(jsonString(declaration.target().kind()));
                    writer.write(", \"binaryName\": ");
                    writer.write(jsonString(declaration.target().binaryName()));
                    writer.write(", \"memberDescriptor\": ");
                    writer.write(jsonString(declaration.target().memberDescriptor()));
                    writer.write("}, \"attributes\": ");
                    writeObject(writer, declaration.attributes());
                    writer.write("}");
                }
                writer.write(sorted.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法写出 Atlas 意图 Manifest", exception);
        }
    }

    private static void writeObject(Writer writer, Map<String, String> values) throws IOException {
        writer.write("{");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            writer.write(index++ == 0 ? "" : ", ");
            writer.write(jsonString(entry.getKey()));
            writer.write(": ");
            writer.write(entry.getValue());
        }
        writer.write("}");
    }

    private static String jsonValue(AnnotationValue annotationValue) {
        Object value = annotationValue.getValue();
        if (value instanceof String string) {
            return jsonString(string);
        }
        if (value instanceof Character character) {
            return jsonString(character.toString());
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof VariableElement enumConstant) {
            return jsonString(enumConstant.getSimpleName().toString());
        }
        if (value instanceof TypeMirror typeMirror) {
            return jsonString(typeMirror.toString());
        }
        if (value instanceof AnnotationMirror nestedAnnotation) {
            return jsonString(nestedAnnotation.toString());
        }
        if (value instanceof List<?> values) {
            List<String> rendered = new ArrayList<>();
            for (Object item : values) {
                rendered.add(jsonValue((AnnotationValue) item));
            }
            return "[" + String.join(",", rendered) + "]";
        }
        return jsonString(Objects.toString(value));
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static String stableId(String value) {
        return value.replaceAll("[^A-Za-z0-9:._#/$-]", "_");
    }

    private record IntentTarget(String kind, String binaryName, String memberDescriptor) {
    }

    private record IntentDeclaration(
            String id,
            String annotationType,
            String assetKind,
            IntentTarget target,
            Map<String, String> attributes) {
    }
}
