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
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            "Character", "Void", "Class", "Number", "Throwable", "Error", "Exception", "RuntimeException",
            "IllegalArgumentException", "Enum", "Comparable", "Iterable", "AutoCloseable");

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
        List<Path> sourceFiles = findProductionJavaSources(request);
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
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            Trees trees = Trees.instance(task);
            Map<String, Set<String>> knownSourceTypesBySimpleName = knownSourceTypesBySimpleName(units);
            for (CompilationUnitTree unit : units) {
                Path sourceFile = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                inspector.accept(new JavaSourceUnit(
                        request, analyzerId, unit, trees.getSourcePositions(), sourceFile, knownSourceTypesBySimpleName));
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

    /**
     * 收集当前分析范围内可由源码本身证明的顶层类型。它专门服务于 {@code import a.b.*}：
     * 只有该导入包下存在唯一同名源码类型时，才把简单名归一为完全限定名；不能由源码证明的外部类不会被猜测。
     */
    private static Map<String, Set<String>> knownSourceTypesBySimpleName(List<CompilationUnitTree> units) {
        Map<String, Set<String>> types = new HashMap<>();
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof com.sun.source.tree.ClassTree type
                        && !type.getSimpleName().toString().isBlank()) {
                    String qualifiedName = packageName.isEmpty()
                            ? type.getSimpleName().toString()
                            : packageName + "." + type.getSimpleName();
                    types.computeIfAbsent(type.getSimpleName().toString(), ignored -> new LinkedHashSet<>())
                            .add(qualifiedName);
                }
            }
        }
        Map<String, Set<String>> immutable = new HashMap<>();
        types.forEach((simpleName, qualifiedNames) -> immutable.put(simpleName, Set.copyOf(qualifiedNames)));
        return Map.copyOf(immutable);
    }

    /**
     * 在请求显式声明的源码根下扫描 Java；未声明时仅发现约定的 {@code src/main/java} 根，找不到约定根才回退至项目根。
     * 配置排除路径和不应成为业务源码的公共目录始终优先于扫描范围。
     */
    public static List<Path> findProductionJavaSources(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        Path projectRoot = request.projectRoot();
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("projectRoot 不是可读取目录: " + projectRoot);
        }
        List<Path> roots = request.sourceRoots().isEmpty()
                ? discoverConventionalSourceRoots(projectRoot)
                : request.sourceRoots();
        LinkedHashSet<Path> sourceFiles = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("sourceRoots 中的目录不存在或不可读取: " + root);
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .filter(path -> isProductionSource(projectRoot.relativize(path)))
                        .filter(path -> request.excludedPaths().stream().noneMatch(path::startsWith))
                        .forEach(sourceFiles::add);
            } catch (IOException exception) {
                throw new IllegalStateException("无法扫描项目 Java 源码: " + root, exception);
            }
        }
        return sourceFiles.stream().sorted().toList();
    }

    private static List<Path> discoverConventionalSourceRoots(Path projectRoot) {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> roots = paths.filter(Files::isDirectory)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> isConventionalJavaRoot(projectRoot.relativize(path)))
                    .sorted().toList();
            return roots.isEmpty() ? List.of(projectRoot) : roots;
        } catch (IOException exception) {
            throw new IllegalStateException("无法发现项目 Java 源码根: " + projectRoot, exception);
        }
    }

    private static boolean isConventionalJavaRoot(Path relativePath) {
        List<String> parts = new ArrayList<>();
        relativePath.forEach(part -> parts.add(part.toString()));
        int size = parts.size();
        return size >= 3 && parts.get(size - 3).equals("src")
                && parts.get(size - 2).equals("main") && parts.get(size - 1).equals("java");
    }

    private static boolean isProductionSource(Path relativePath) {
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.equals("target") || value.equals("build") || value.equals("out") || value.equals(".git")
                    || value.equals(".idea") || value.equals(".gradle") || value.equals("node_modules")
                    || value.equals(".tmp") || value.equals("test")) {
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
        private final Set<String> onDemandImportPackages;
        private final Map<String, Set<String>> knownSourceTypesBySimpleName;

        private JavaSourceUnit(
                AnalysisRequest request,
                String analyzerId,
                CompilationUnitTree compilationUnit,
                SourcePositions sourcePositions,
                Path sourceFile,
                Map<String, Set<String>> knownSourceTypesBySimpleName) {
            this.request = request;
            this.analyzerId = analyzerId;
            this.compilationUnit = compilationUnit;
            this.sourcePositions = sourcePositions;
            this.sourceFile = sourceFile;
            this.packageName = compilationUnit.getPackageName() == null ? "" : compilationUnit.getPackageName().toString();
            this.explicitImports = importsOf(compilationUnit);
            this.onDemandImportPackages = onDemandImportPackagesOf(compilationUnit);
            this.knownSourceTypesBySimpleName = knownSourceTypesBySimpleName;
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
            if (type == null) {
                return "";
            }
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
            // Enum 常量、部分错误恢复树和匿名声明的 VariableTree.getType() 可以为 null。解析器不能因为一个
            // 非类型节点而中断整个工程的 Spring/Axon/JPA 扫描；未知类型仍要以明确占位保留在事实中。
            if (type == null) {
                return "<inferred>";
            }
            return normalizeTypeText(type.toString().replace("...", "[]"));
        }

        /** 将注解中 {@code Foo.class} 等文本类型引用按同一 import/同包规则归一。 */
        public String normalizeTypeText(String raw) {
            raw = Objects.requireNonNull(raw, "raw 不能为空").replace(".class", "").replace("...", "[]").trim();
            if (raw.isEmpty()) {
                return "<inferred>";
            }
            int genericStart = raw.indexOf('<');
            String suffix = "";
            if (genericStart >= 0) {
                suffix = raw.substring(raw.lastIndexOf('>') + 1);
                raw = raw.substring(0, genericStart);
            }
            if (raw.contains(".")) {
                String firstSegment = raw.substring(0, raw.indexOf('.'));
                String imported = explicitImports.get(firstSegment);
                if (imported != null) {
                    return imported + raw.substring(firstSegment.length()) + suffix;
                }
                Optional<String> wildcardResolved = resolveImportedSourceType(firstSegment);
                if (wildcardResolved.isPresent()) {
                    return wildcardResolved.get() + raw.substring(firstSegment.length()) + suffix;
                }
                return raw + suffix;
            }
            if (JAVA_LANG_TYPES.contains(raw) || raw.equals("boolean") || raw.equals("byte") || raw.equals("short")
                    || raw.equals("int") || raw.equals("long") || raw.equals("float") || raw.equals("double")
                    || raw.equals("char") || raw.equals("void")) {
                return (JAVA_LANG_TYPES.contains(raw) ? "java.lang." : "") + raw + suffix;
            }
            String explicitlyImported = explicitImports.get(raw);
            if (explicitlyImported != null) {
                return explicitlyImported + suffix;
            }
            return resolveImportedSourceType(raw)
                    .orElse(packageName.isEmpty() ? raw : packageName + "." + raw) + suffix;
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

        private static Set<String> onDemandImportPackagesOf(CompilationUnitTree unit) {
            Set<String> packages = new LinkedHashSet<>();
            unit.getImports().forEach(importTree -> {
                if (!importTree.isStatic()) {
                    String qualified = importTree.getQualifiedIdentifier().toString();
                    if (qualified.endsWith(".*")) {
                        packages.add(qualified.substring(0, qualified.length() - 2));
                    }
                }
            });
            return Set.copyOf(packages);
        }

        /**
         * Java 的按需导入只在当前源码集合中存在唯一候选时归一化。多个候选或只在 classpath 中的候选都不能
         * 由纯源码模式可靠证明，因此保持原文本，让上层把它作为未决而不是一条伪精确边。
         */
        private Optional<String> resolveImportedSourceType(String simpleName) {
            Set<String> candidates = knownSourceTypesBySimpleName.getOrDefault(simpleName, Set.of()).stream()
                    .filter(candidate -> onDemandImportPackages.contains(
                            candidate.substring(0, candidate.lastIndexOf('.'))))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return candidates.size() == 1 ? Optional.of(candidates.iterator().next()) : Optional.empty();
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
