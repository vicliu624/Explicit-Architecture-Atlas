package io.github.vicliu624.explicitarchitecture.atlas.processor;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasAggregate;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasContext;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEndpoint;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEvent;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProjection;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

/**
 * 将源码中的 Atlas 意图标注写成稳定的 JSON 资源。
 *
 * <p>处理器只提取开发者明确声明的意图，不猜测 Spring/Axon 等技术事实；后者由独立分析器负责。</p>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasContext",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasAggregate",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasConstraint",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEvent",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProjection",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProcessManager",
        "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEndpoint"
})
public final class AtlasIntentProcessor extends AbstractProcessor {

    private static final List<Class<?>> SUPPORTED_ANNOTATIONS = List.of(
            AtlasContext.class,
            AtlasAggregate.class,
            AtlasConstraint.class,
            AtlasEvent.class,
            AtlasProjection.class,
            AtlasProcessManager.class,
            AtlasEndpoint.class);

    private final Set<IntentDeclaration> declarations = new LinkedHashSet<>();
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Class<?> annotation : SUPPORTED_ANNOTATIONS) {
            TypeElement annotationElement = processingEnv.getElementUtils().getTypeElement(annotation.getName());
            if (annotationElement == null) {
                continue;
            }
            for (Element element : roundEnvironment.getElementsAnnotatedWith(annotationElement)) {
                declarations.add(new IntentDeclaration(
                        annotation.getSimpleName(),
                        element.getKind().name(),
                        element.toString(),
                        annotationAttributes(element, annotationElement)));
            }
        }

        if (roundEnvironment.processingOver() && !written) {
            writeIndex();
            written = true;
        }
        return false;
    }

    private void writeIndex() {
        List<IntentDeclaration> sorted = declarations.stream()
                .sorted(Comparator.comparing(IntentDeclaration::annotation)
                        .thenComparing(IntentDeclaration::element))
                .toList();
        Filer filer = processingEnv.getFiler();
        try {
            FileObject resource = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/explicit-architecture-atlas/atlas-intent.json");
            try (Writer writer = resource.openWriter()) {
                writer.write("{\n  \"schemaVersion\": \"0.1.0\",\n  \"declarations\": [");
                for (int index = 0; index < sorted.size(); index++) {
                    IntentDeclaration declaration = sorted.get(index);
                    writer.write(index == 0 ? "\n" : ",\n");
                    writer.write("    {\"annotation\": \"");
                    writer.write(escape(declaration.annotation()));
                    writer.write("\", \"elementKind\": \"");
                    writer.write(escape(declaration.elementKind()));
                    writer.write("\", \"element\": \"");
                    writer.write(escape(declaration.element()));
                    writer.write("\", \"attributes\": {");
                    int attributeIndex = 0;
                    for (Map.Entry<String, String> attribute : declaration.attributes().entrySet()) {
                        writer.write(attributeIndex++ == 0 ? "" : ", ");
                        writer.write("\"");
                        writer.write(escape(attribute.getKey()));
                        writer.write("\": \"");
                        writer.write(escape(attribute.getValue()));
                        writer.write("\"");
                    }
                    writer.write("}}");
                }
                writer.write(sorted.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法写出 Atlas 意图索引", exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private Map<String, String> annotationAttributes(Element element, TypeElement annotationElement) {
        AnnotationMirror mirror = element.getAnnotationMirrors().stream()
                .filter(candidate -> candidate.getAnnotationType().asElement().equals(annotationElement))
                .findFirst()
                .orElseThrow();
        Map<String, String> attributes = new TreeMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            attributes.put(entry.getKey().getSimpleName().toString(), annotationValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(attributes);
    }

    private static String annotationValue(AnnotationValue annotationValue) {
        Object value = annotationValue.getValue();
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(AnnotationValue.class::cast)
                    .map(AtlasIntentProcessor::annotationValue)
                    .reduce((left, right) -> left + "," + right)
                    .map(joined -> "[" + joined + "]")
                    .orElse("[]");
        }
        return value.toString();
    }

    private record IntentDeclaration(
            String annotation,
            String elementKind,
            String element,
            Map<String, String> attributes) {
    }
}
