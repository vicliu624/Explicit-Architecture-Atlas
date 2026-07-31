package io.github.vicliu624.explicitarchitecture.atlas.source.jvm;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 面向 JVM 框架适配器的只读 Java 源码语法树入口。
 *
 * <p>Spring、Axon 等适配器共用此类以获得相同的生产源码过滤、源码定位、UTF-8 读取和证据格式。
 * 它只进行语法解析，不加载业务工程的 classpath、不触发注解处理器，也不反射加载业务类型。</p>
 */
public final class JavaSourceAnalysis {

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Object", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double",
            "Character", "Void", "Exception", "RuntimeException", "IllegalArgumentException", "Enum");

    private JavaSourceAnalysis() {
    }

    /** 解析项目生产 Java 源文件，并在编译器资源仍有效时回调每个语法单元。 */
    public static ParseOutcome inspectProductionSources(
            AnalysisRequest request,
            String analyzerId,
            Consumer<JavaSourceUnit> inspector) {
        Objects.requireNonNull(request, "request 不能为空");
        Objects.requireNonNull(analyzerId, "analyzerId 不能为空");
        Objects.requireNonNull(inspector, "inspector 不能为空");
        List<Path> sourceFiles = findProductionJavaSources(request.projectRoot());
        if (sourceFiles.isEmpty()) {
            return new ParseOutcome(0, true, List.of());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new ParseOutcome(sourceFiles.size(), false, List.of(new AtlasDiagnostic(
                    AtlasId.of("diagnostic:" + analyzerId + ":compiler-unavailable"),
                    DiagnosticSeverity.ERROR,
                    "JVM_COMPILER_UNAVAILABLE",
                    "当前 JVM 未提供 JavaCompiler，无法安全解析源码",
                    java.util.Optional.empty())));
        }

        DiagnosticCollector<JavaFileObject> compilerDiagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                compilerDiagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sourceUnits = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, compilerDiagnostics, List.of("-proc:none"), null, sourceUnits);
            Iterable<? extends CompilationUnitTree> units = task.parse();
            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : units) {
                Path sourceFile = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                inspector.accept(new JavaSourceUnit(request, analyzerId, unit, trees.getSourcePositions(), sourceFile));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Java 源文件", exception);
        }
        return new ParseOutcome(sourceFiles.size(), true, compilerDiagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR || diagnostic.getKind() == Diagnostic.Kind.WARNING)
                .map(diagnostic -> compilerDiagnostic(analyzerId, diagnostic))
                .sorted(Comparator.comparing(AtlasDiagnostic::id))
                .toList());
    }

    private static List<Path> findProductionJavaSources(Path projectRoot) {
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("projectRoot 不是可读取目录: " + projectRoot);
        }
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> isProductionSource(projectRoot.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("无法扫描项目 Java 源码: " + projectRoot, exception);
        }
    }

    private static boolean isProductionSource(Path relativePath) {
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.equals("target") || value.equals(".git") || value.equals(".idea") || value.equals("node_modules")
                    || value.equals("test")) {
                return false;
            }
        }
        return true;
    }

    private static AtlasDiagnostic compilerDiagnostic(String analyzerId, Diagnostic<? extends JavaFileObject> diagnostic) {
        String identity = diagnostic.getKind() + ":" + diagnostic.getSource() + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getCode();
        DiagnosticSeverity severity = diagnostic.getKind() == Diagnostic.Kind.ERROR ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARNING;
        return new AtlasDiagnostic(
                AtlasId.of("diagnostic:" + analyzerId + ":" + encode(identity)),
                severity,
                "JAVAC_" + diagnostic.getCode().replace('.', '_').toUpperCase(),
                diagnostic.getMessage(null),
                java.util.Optional.empty());
    }

    /** 一次源扫描的总体结果。 */
    public record ParseOutcome(int sourceFileCount, boolean compilerAvailable, List<AtlasDiagnostic> diagnostics) {
        public ParseOutcome {
            if (sourceFileCount < 0) {
                throw new IllegalArgumentException("sourceFileCount 不能为负数");
            }
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空"));
        }

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
        }
    }

    /** 单个 Java 语法单元及其可复用的安全定位辅助方法。 */
    public static final class JavaSourceUnit {

        private final AnalysisRequest request;
        private final String analyzerId;
        private final CompilationUnitTree compilationUnit;
        private final SourcePositions sourcePositions;
        private final Path sourceFile;
        private final String packageName;
        private final Map<String, String> explicitImports;

        private JavaSourceUnit(
                AnalysisRequest request,
                String analyzerId,
                CompilationUnitTree compilationUnit,
                SourcePositions sourcePositions,
                Path sourceFile) {
            this.request = request;
            this.analyzerId = analyzerId;
            this.compilationUnit = compilationUnit;
            this.sourcePositions = sourcePositions;
            this.sourceFile = sourceFile;
            this.packageName = compilationUnit.getPackageName() == null ? "" : compilationUnit.getPackageName().toString();
            this.explicitImports = importsOf(compilationUnit);
        }

        public CompilationUnitTree compilationUnit() {
            return compilationUnit;
        }

        public String packageName() {
            return packageName;
        }

        public String relativeSourcePath() {
            return request.projectRoot().relativize(sourceFile).toString().replace('\\', '/');
        }

        public String simpleName(Tree type) {
            String value = type.toString();
            int genericStart = value.indexOf('<');
            if (genericStart >= 0) {
                value = value.substring(0, genericStart);
            }
            int dot = value.lastIndexOf('.');
            return dot < 0 ? value : value.substring(dot + 1);
        }

        /** 将源码类型按显式 import、同包和 java.lang 规则归一为稳定文本。 */
        public String normalizeType(Tree type) {
            String raw = type.toString().replace("...", "[]").trim();
            int genericStart = raw.indexOf('<');
            String suffix = "";
            if (genericStart >= 0) {
                suffix = raw.substring(raw.lastIndexOf('>') + 1);
                raw = raw.substring(0, genericStart);
            }
            if (raw.contains(".")) {
                String firstSegment = raw.substring(0, raw.indexOf('.'));
                String imported = explicitImports.get(firstSegment);
                return (imported == null ? raw : imported + raw.substring(firstSegment.length())) + suffix;
            }
            if (JAVA_LANG_TYPES.contains(raw) || raw.equals("boolean") || raw.equals("byte") || raw.equals("short")
                    || raw.equals("int") || raw.equals("long") || raw.equals("float") || raw.equals("double")
                    || raw.equals("char") || raw.equals("void")) {
                return (JAVA_LANG_TYPES.contains(raw) ? "java.lang." : "") + raw + suffix;
            }
            return explicitImports.getOrDefault(raw, packageName.isEmpty() ? raw : packageName + "." + raw) + suffix;
        }

        /** 取得注解的完全限定名称；未导入的简单名保留原文本，避免不可靠猜测。 */
        public String annotationType(AnnotationTree annotation) {
            String raw = annotation.getAnnotationType().toString();
            if (raw.contains(".")) {
                String firstSegment = raw.substring(0, raw.indexOf('.'));
                String imported = explicitImports.get(firstSegment);
                return imported == null ? raw : imported + raw.substring(firstSegment.length());
            }
            return explicitImports.getOrDefault(raw, raw);
        }

        public String annotationSimpleName(AnnotationTree annotation) {
            String type = annotationType(annotation);
            int separator = type.lastIndexOf('.');
            return separator < 0 ? type : type.substring(separator + 1);
        }

        public SourceRange rangeOf(Tree tree) {
            long start = sourcePositions.getStartPosition(compilationUnit, tree);
            long end = sourcePositions.getEndPosition(compilationUnit, tree);
            if (start < 0 || end < 0 || compilationUnit.getLineMap() == null) {
                return SourceRange.unknown();
            }
            return new SourceRange(
                    (int) compilationUnit.getLineMap().getLineNumber(start),
                    (int) compilationUnit.getLineMap().getColumnNumber(start),
                    (int) compilationUnit.getLineMap().getLineNumber(end),
                    (int) compilationUnit.getLineMap().getColumnNumber(end));
        }

        /** 用源码位置生成一个带 {@code SOURCE_CODE} 证据的不可变事实。 */
        public SourceFact fact(FactKind kind, String subject, Map<String, AtlasValue> attributes, Tree tree, String summary) {
            Objects.requireNonNull(kind, "kind 不能为空");
            Objects.requireNonNull(subject, "subject 不能为空");
            Objects.requireNonNull(attributes, "attributes 不能为空");
            Objects.requireNonNull(tree, "tree 不能为空");
            SourceRange range = rangeOf(tree);
            String identity = analyzerId + ":" + kind.name() + ":" + relativeSourcePath() + ":" + range.startLine()
                    + ":" + range.startColumn() + ":" + subject;
            Evidence evidence = new Evidence(
                    AtlasId.of("evidence:" + analyzerId + ":" + encode(identity)),
                    EvidenceOrigin.SOURCE_CODE,
                    EvidenceClaim.DERIVED,
                    EvidenceStatus.VERIFIED,
                    new SourceLocator(projectUri(), subject, range),
                    request.sourceRevision(),
                    EvidenceVisibility.EXPERT_ONLY,
                    analyzerId,
                    summary == null || summary.isBlank() ? kind + " 由 Java 源码语法树提取" : summary.trim());
            return new SourceFact(
                    AtlasId.of("fact:" + analyzerId + ":" + encode(identity)),
                    analyzerId,
                    kind,
                    subject,
                    attributes,
                    List.of(evidence));
        }

        private String projectUri() {
            String encodedPath = Stream.of(relativeSourcePath().split("/"))
                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                    .reduce((left, right) -> left + "/" + right)
                    .orElse("");
            return "project://" + request.projectId().value() + "/" + encodedPath;
        }

        private static Map<String, String> importsOf(CompilationUnitTree unit) {
            Map<String, String> imports = new HashMap<>();
            unit.getImports().forEach(importTree -> {
                if (!importTree.isStatic()) {
                    String qualified = importTree.getQualifiedIdentifier().toString();
                    if (!qualified.endsWith(".*")) {
                        imports.put(qualified.substring(qualified.lastIndexOf('.') + 1), qualified);
                    }
                }
            });
            return Map.copyOf(imports);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
