package io.github.vicliu624.explicitarchitecture.atlas.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasIntentProcessorTest {

    @TempDir
    Path output;

    @Test
    void emitsAParameterTargetForTypeUseInputIntent() throws Exception {
        Compilation compilation = compile("""
                package fixture;
                import io.github.vicliu624.explicitarchitecture.atlas.annotation.*;
                final class Sample {
                    @AtlasOperation(id = "operation:sample", name = "sample", purpose = "sample")
                    static String sample(@AtlasInput(id = "input:sample", name = "sample input") String value) { return value; }
                }
                """);

        assertTrue(compilation.success(), () -> "有效注解不应编译失败: " + compilation.diagnostics());
        String manifest = Files.readString(output.resolve("META-INF/explicit-architecture-atlas/atlas-intent.json"));
        assertTrue(manifest.contains("\"kind\": \"PARAMETER\""));
        assertTrue(manifest.contains("sample(java.lang.String)::parameter[0]:value"));
    }

    @Test
    void rejectsOutOfRangeGuardParameterIndexBeforeWritingItAsIntent() throws Exception {
        Compilation compilation = compile("""
                package fixture;
                import io.github.vicliu624.explicitarchitecture.atlas.annotation.*;
                final class Sample {
                    @AtlasGuard(id = "guard:invalid", kind = AtlasGuardKind.REQUIRED, subjectParameter = 1)
                    static void require(String value) { }
                }
                """);

        assertFalse(compilation.success());
        assertTrue(compilation.diagnostics().stream().anyMatch(value -> value.contains("subjectParameter")));
    }

    private Compilation compile(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("测试 JVM 未提供 JavaCompiler");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            manager.setLocationFromPaths(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(output));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, manager, diagnostics, List.of("--release", "21", "-proc:only"), null,
                    List.of(new Source("fixture.Sample", source)));
            task.setProcessors(List.of(new AtlasIntentProcessor()));
            boolean success = task.call();
            return new Compilation(success, diagnostics.getDiagnostics().stream().map(Object::toString).toList());
        }
    }

    private record Compilation(boolean success, List<String> diagnostics) {
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String source;

        private Source(String name, String source) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
