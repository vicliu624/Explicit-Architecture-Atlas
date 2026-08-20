package io.github.vicliu624.explicitarchitecture.atlas.processor;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasIntent;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
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
import javax.tools.Diagnostic;
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
    private final Set<String> explicitAssetIds = new LinkedHashSet<>();
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
        Map<String, AnnotationMirror> annotations = new LinkedHashMap<>();
        element.getAnnotationMirrors().forEach(annotation -> annotations.putIfAbsent(annotationKey(annotation), annotation));
        // TYPE_USE 注解在 javax.lang.model 中附着在 TypeMirror 而非 Element；如果忽略它，@AtlasInput/@AtlasOutput
        // 写在参数/返回类型前时会悄然丢失，恰好破坏函数契约最需要的细粒度定位。
        element.asType().getAnnotationMirrors().forEach(annotation -> annotations.putIfAbsent(annotationKey(annotation), annotation));
        if (element instanceof ExecutableElement executable) {
            executable.getReturnType().getAnnotationMirrors()
                    .forEach(annotation -> annotations.putIfAbsent(annotationKey(annotation), annotation));
        }
        for (AnnotationMirror annotation : annotations.values()) {
            Optional<String> assetKind = atlasIntentKind(annotation);
            if (assetKind.isPresent()) {
                if (validateDeclaration(element, annotation)) {
                    declarations.add(toDeclaration(element, annotation, assetKind.orElseThrow()));
                }
            }
        }
        for (Element enclosed : element.getEnclosedElements()) {
            collectDeclarations(enclosed);
        }
        if (element instanceof ExecutableElement executable) {
            // 方法参数不属于 getEnclosedElements()；必须显式遍历，否则参数和 TYPE_USE 输入意图不会进入 manifest。
            for (VariableElement parameter : executable.getParameters()) {
                collectDeclarations(parameter);
            }
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

    /**
     * 对 Atlas 自带注解执行可在编译期证明的最小语义校验；第三方基于 {@link AtlasIntent} 的注解仍保持开放扩展。
     * 不合法声明不写入 manifest，避免下游把一个编译器已经判定错误的意图当成可信事实。
     */
    private boolean validateDeclaration(Element element, AnnotationMirror annotation) {
        String simpleName = annotation.getAnnotationType().asElement().getSimpleName().toString();
        boolean valid = validateTarget(simpleName, element, annotation);
        Optional<String> stableId = Optional.empty();
        if (requiresStableId(simpleName)) {
            Optional<String> id = stringAttribute(annotation, "id");
            if (id.isEmpty() || id.orElseThrow().isBlank()) {
                error(element, annotation, simpleName + " 必须声明非空稳定 id");
                valid = false;
            } else if (explicitAssetIds.contains(id.orElseThrow())) {
                error(element, annotation, "Atlas 稳定 id 重复: " + id.orElseThrow());
                valid = false;
            } else {
                stableId = id;
            }
        }
        if (simpleName.equals("AtlasGuard")) {
            valid &= validateGuardIndexes(element, annotation);
        }
        if (simpleName.equals("AtlasPrecondition") || simpleName.equals("AtlasPostcondition")) {
            List<String> rules = stringListAttribute(annotation, "rules");
            if (rules.isEmpty() || rules.stream().anyMatch(String::isBlank)) {
                error(element, annotation, simpleName + " 至少需要一个非空规则 ID");
                valid = false;
            }
        }
        if (valid) {
            stableId.ifPresent(explicitAssetIds::add);
        }
        return valid;
    }

    private boolean validateTarget(String annotation, Element element, AnnotationMirror mirror) {
        Set<ElementKind> expected = switch (annotation) {
            case "AtlasDomainPrimitive", "AtlasAggregate", "AtlasContext", "AtlasEvent", "AtlasProjection", "AtlasProcessManager" ->
                    Set.of(ElementKind.CLASS, ElementKind.INTERFACE, ElementKind.ENUM, ElementKind.RECORD);
            case "AtlasGuard", "AtlasOperation", "AtlasOperationSummary", "AtlasOutput", "AtlasPrecondition", "AtlasPostcondition",
                    "AtlasEffect", "AtlasFailure" -> Set.of(ElementKind.METHOD, ElementKind.CONSTRUCTOR);
            case "AtlasInput" -> Set.of(ElementKind.PARAMETER, ElementKind.FIELD, ElementKind.RECORD_COMPONENT);
            default -> Set.of();
        };
        if (!expected.isEmpty() && !expected.contains(element.getKind())) {
            error(element, mirror, annotation + " 不能标注在 " + element.getKind() + " 上");
            return false;
        }
        return true;
    }

    private boolean validateGuardIndexes(Element element, AnnotationMirror annotation) {
        if (!(element instanceof ExecutableElement method)) {
            return false;
        }
        int parameterCount = method.getParameters().size();
        boolean valid = true;
        Map<Integer, String> assignedRoles = new LinkedHashMap<>();
        for (String name : List.of("subjectParameter", "predicateParameter", "patternParameter", "messageParameter")) {
            int index = integerAttribute(annotation, name).orElse(-1);
            if (index < -1 || index >= parameterCount) {
                error(element, annotation, "@AtlasGuard 的 " + name + " 必须在 -1 到 " + Math.max(-1, parameterCount - 1) + " 之间");
                valid = false;
                continue;
            }
            if (index >= 0 && assignedRoles.putIfAbsent(index, name) != null) {
                error(element, annotation, "@AtlasGuard 的参数索引不能同时承担多个角色: #" + index + "（"
                        + assignedRoles.get(index) + " 与 " + name + "）");
                valid = false;
            }
        }
        return valid;
    }

    private Optional<Integer> integerAttribute(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name) && entry.getValue().getValue() instanceof Integer value) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private List<String> stringListAttribute(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name) && entry.getValue().getValue() instanceof List<?> values) {
                return values.stream().map(AnnotationValue.class::cast).map(AnnotationValue::getValue)
                        .filter(String.class::isInstance).map(String.class::cast).toList();
            }
        }
        return List.of();
    }

    private void error(Element element, AnnotationMirror annotation, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    private static boolean requiresStableId(String annotation) {
        return Set.of("AtlasDomainPrimitive", "AtlasGuard", "AtlasRuleSet", "AtlasRule", "AtlasOperation", "AtlasInput", "AtlasOutput",
                "AtlasAdapter", "AtlasOperationSummary", "AtlasMigrationMapping", "AtlasCompatibilityTarget").contains(annotation);
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
            case PARAMETER -> parameterDescriptor((VariableElement) element);
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

    private String methodDescriptor(ExecutableElement method) {
        List<String> parameterTypes = method.getParameters().stream()
                .map(VariableElement::asType)
                .map(type -> typeText(processingEnv.getTypeUtils().erasure(type)))
                .toList();
        return method.getSimpleName() + "(" + String.join(",", parameterTypes) + ")";
    }

    private String parameterDescriptor(VariableElement parameter) {
        if (!(parameter.getEnclosingElement() instanceof ExecutableElement owner)) {
            return parameter.getSimpleName().toString();
        }
        int index = owner.getParameters().indexOf(parameter);
        return methodDescriptor(owner) + "::parameter[" + index + "]:" + parameter.getSimpleName();
    }

    private static String annotationKey(AnnotationMirror annotation) {
        return annotation.getAnnotationType().asElement() + ":" + annotation;
    }

    private static String typeText(TypeMirror type) {
        // TypeMirror#toString 保留 TYPE_USE 注解；它们属于 intent 元数据，不能进入 Java 方法身份。
        return type.toString().replaceAll("@[A-Za-z0-9_.$]+(?:\\([^)]*\\))?\\s*", "");
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
