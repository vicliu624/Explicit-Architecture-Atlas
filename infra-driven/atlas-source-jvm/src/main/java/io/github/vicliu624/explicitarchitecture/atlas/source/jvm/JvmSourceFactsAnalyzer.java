package io.github.vicliu624.explicitarchitecture.atlas.source.jvm;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 使用 JDK 编译器语法树提取 Java 源码的结构、注解、显式校验和调用事实。
 *
 * <p>本适配器刻意不依赖业务工程的 classpath 或任意反射加载；类型无法可靠解析时仍会输出带源码证据的
 * {@link CapabilityStatus#PARTIAL} 事实，而不会把静态推断伪装成运行时真相。</p>
 */
public final class JvmSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:jvm-source"), "JVM 源码分析器", "JVM 源码、Javadoc、约束与调用事实");
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Object", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double",
            "Character", "Void", "Exception", "RuntimeException", "IllegalArgumentException", "Enum");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<Path> sourceFiles = findProductionJavaSources(request.projectRoot());
        if (sourceFiles.isEmpty()) {
            return new AnalyzerContribution(
                    new CoverageReport(DESCRIPTOR.id().value(), CapabilityStatus.NOT_APPLICABLE, request.scopeUri(),
                            "项目范围内没有可分析的生产 Java 源文件"),
                    List.of(), List.of(), List.of());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new AnalyzerContribution(
                    new CoverageReport(DESCRIPTOR.id().value(), CapabilityStatus.FAILED, request.scopeUri(),
                            "当前 JVM 未提供 JavaCompiler，无法安全解析源码"),
                    List.of(), List.of(), List.of(diagnostic(request, "JVM_COMPILER_UNAVAILABLE", "当前 JVM 未提供 JavaCompiler")));
        }

        DiagnosticCollector<JavaFileObject> compilerDiagnostics = new DiagnosticCollector<>();
        List<SourceFact> facts = new ArrayList<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(compilerDiagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, compilerDiagnostics, List.of("-proc:none"), null, units);
            Iterable<? extends CompilationUnitTree> compilationUnits = task.parse();
            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : compilationUnits) {
                new SourceScanner(request, unit, trees).scan(unit, facts);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Java 源文件", exception);
        }

        List<AtlasDiagnostic> diagnostics = compilerDiagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR || diagnostic.getKind() == Diagnostic.Kind.WARNING)
                .map(diagnostic -> compilerDiagnostic(request, diagnostic))
                .sorted(Comparator.comparing(AtlasDiagnostic::id))
                .toList();
        CapabilityStatus status = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
                ? CapabilityStatus.PARTIAL
                : CapabilityStatus.COMPLETE;
        String summary = status == CapabilityStatus.COMPLETE
                ? "已解析 " + sourceFiles.size() + " 个生产 Java 源文件"
                : "已部分解析 " + sourceFiles.size() + " 个生产 Java 源文件；请查看解析诊断";
        return new AnalyzerContribution(
                new CoverageReport(DESCRIPTOR.id().value(), status, request.scopeUri(), summary),
                facts.stream().sorted(Comparator.comparing(SourceFact::id)).toList(),
                List.of(),
                diagnostics);
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

    private static AtlasDiagnostic compilerDiagnostic(AnalysisRequest request, Diagnostic<? extends JavaFileObject> diagnostic) {
        String identity = diagnostic.getKind() + ":" + diagnostic.getSource() + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getCode();
        DiagnosticSeverity severity = diagnostic.getKind() == Diagnostic.Kind.ERROR ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARNING;
        return new AtlasDiagnostic(
                AtlasId.of("diagnostic:jvm-source:" + encode(identity)),
                severity,
                "JAVAC_" + diagnostic.getCode().replace('.', '_').toUpperCase(),
                diagnostic.getMessage(null),
                java.util.Optional.empty());
    }

    private static AtlasDiagnostic diagnostic(AnalysisRequest request, String code, String message) {
        return new AtlasDiagnostic(
                AtlasId.of("diagnostic:jvm-source:" + encode(code)),
                DiagnosticSeverity.ERROR,
                code,
                message,
                java.util.Optional.empty());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class SourceScanner extends TreePathScanner<Void, List<SourceFact>> {

        private final AnalysisRequest request;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final Path sourceFile;
        private final String packageName;
        private final Map<String, String> explicitImports;
        private final List<String> typeStack = new ArrayList<>();
        private String currentMethodSubject;

        private SourceScanner(AnalysisRequest request, CompilationUnitTree unit, Trees trees) {
            this.request = request;
            this.unit = unit;
            this.positions = trees.getSourcePositions();
            this.sourceFile = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            this.explicitImports = importsOf(unit);
        }

        @Override
        public Void visitClass(ClassTree node, List<SourceFact> facts) {
            String simpleName = node.getSimpleName().toString();
            typeStack.add(simpleName);
            String qualifiedName = qualifiedTypeName();
            facts.add(fact(FactKind.SOURCE_TYPE, qualifiedName,
                    Map.of("declarationKind", AtlasValue.text(node.getKind().name())), node));
            try {
                return super.visitClass(node, facts);
            } finally {
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, List<SourceFact> facts) {
            String previousMethod = currentMethodSubject;
            String owner = qualifiedTypeName();
            String methodName = node.getName().contentEquals("<init>") ? "<init>" : node.getName().toString();
            String descriptor = methodName + "(" + node.getParameters().stream()
                    .map(VariableTree::getType)
                    .map(this::normalizeType)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + ")";
            currentMethodSubject = owner + "#" + descriptor;
            facts.add(fact(FactKind.SOURCE_METHOD, currentMethodSubject,
                    Map.of("owner", AtlasValue.text(owner), "memberDescriptor", AtlasValue.text(descriptor)), node));
            try {
                return super.visitMethod(node, facts);
            } finally {
                currentMethodSubject = previousMethod;
            }
        }

        @Override
        public Void visitVariable(VariableTree node, List<SourceFact> facts) {
            if (currentMethodSubject == null && !typeStack.isEmpty()) {
                String subject = qualifiedTypeName() + "#" + node.getName();
                facts.add(fact(FactKind.SOURCE_FIELD, subject,
                        Map.of("owner", AtlasValue.text(qualifiedTypeName()), "type", AtlasValue.text(normalizeType(node.getType()))), node));
            }
            return super.visitVariable(node, facts);
        }

        @Override
        public Void visitAnnotation(AnnotationTree node, List<SourceFact> facts) {
            String target = currentMethodSubject == null ? qualifiedTypeName() : currentMethodSubject;
            String annotationType = node.getAnnotationType().toString();
            facts.add(fact(FactKind.ANNOTATION, annotationType + " on " + target,
                    Map.of("annotationType", AtlasValue.text(annotationType), "target", AtlasValue.text(target)), node));
            return super.visitAnnotation(node, facts);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, List<SourceFact> facts) {
            Invocation invocation = invocationOf(node.getMethodSelect());
            if (currentMethodSubject != null) {
                facts.add(fact(FactKind.METHOD_CALL, currentMethodSubject + " -> " + node.getMethodSelect(),
                        Map.of(
                                "caller", AtlasValue.text(currentMethodSubject),
                                "calleeOwnerHint", AtlasValue.text(invocation.ownerHint()),
                                "calleeMethodName", AtlasValue.text(invocation.methodName()),
                                "calleeExpression", AtlasValue.text(node.getMethodSelect().toString())),
                        node));
            }
            validationFact(node, invocation, facts);
            return super.visitMethodInvocation(node, facts);
        }

        private void validationFact(MethodInvocationTree node, Invocation invocation, List<SourceFact> facts) {
            String methodName = invocation.methodName();
            boolean isPatternCompile = methodName.equals("compile") && invocation.ownerHint().endsWith("Pattern");
            boolean isValidationGuard = methodName.equals("matches") || methodName.equals("requireTrue")
                    || methodName.equals("requireNonBlank") || methodName.equals("requireNonNull");
            if (!isPatternCompile && !isValidationGuard) {
                return;
            }
            String expression = node.getArguments().isEmpty() ? node.toString() : node.getArguments().getFirst().toString();
            String ruleType = isPatternCompile ? "PATTERN" : "GUARD";
            String owner = currentMethodSubject == null ? qualifiedTypeName() : currentMethodSubject;
            facts.add(fact(FactKind.VALIDATION_RULE, owner + " :: " + ruleType + " :: " + expression,
                    Map.of(
                            "owner", AtlasValue.text(owner),
                            "ruleType", AtlasValue.text(ruleType),
                            "expression", AtlasValue.text(expression),
                            "enforcingCall", AtlasValue.text(node.getMethodSelect().toString())),
                    node));
        }

        private SourceFact fact(FactKind kind, String subject, Map<String, AtlasValue> attributes, Tree node) {
            SourceRange range = rangeOf(node);
            String relativeSource = relativeSourcePath();
            String identity = kind.name() + ":" + relativeSource + ":" + range.startLine() + ":" + range.startColumn() + ":" + subject;
            Evidence evidence = new Evidence(
                    AtlasId.of("evidence:jvm-source:" + encode(identity)),
                    EvidenceOrigin.SOURCE_CODE,
                    EvidenceClaim.DERIVED,
                    EvidenceStatus.VERIFIED,
                    new SourceLocator(projectUri(relativeSource), subject, range),
                    request.sourceRevision(),
                    EvidenceVisibility.EXPERT_ONLY,
                    DESCRIPTOR.id().value(),
                    kind + " 由 Java 源码语法树提取");
            return new SourceFact(
                    AtlasId.of("fact:jvm-source:" + encode(identity)),
                    DESCRIPTOR.id().value(),
                    kind,
                    subject,
                    attributes,
                    List.of(evidence));
        }

        private String qualifiedTypeName() {
            String nesting = String.join("$", typeStack);
            return packageName.isEmpty() ? nesting : packageName + "." + nesting;
        }

        private String normalizeType(Tree type) {
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

        private SourceRange rangeOf(Tree tree) {
            long start = positions.getStartPosition(unit, tree);
            long end = positions.getEndPosition(unit, tree);
            if (start < 0 || end < 0 || unit.getLineMap() == null) {
                return SourceRange.unknown();
            }
            return new SourceRange(
                    (int) unit.getLineMap().getLineNumber(start),
                    (int) unit.getLineMap().getColumnNumber(start),
                    (int) unit.getLineMap().getLineNumber(end),
                    (int) unit.getLineMap().getColumnNumber(end));
        }

        private String relativeSourcePath() {
            Path relative = request.projectRoot().relativize(sourceFile);
            return relative.toString().replace('\\', '/');
        }

        private String projectUri(String relativeSource) {
            String encodedPath = Stream.of(relativeSource.split("/"))
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

        private static Invocation invocationOf(ExpressionTree methodSelect) {
            if (methodSelect instanceof MemberSelectTree memberSelect) {
                return new Invocation(memberSelect.getExpression().toString(), memberSelect.getIdentifier().toString());
            }
            if (methodSelect instanceof IdentifierTree identifier) {
                return new Invocation("", identifier.getName().toString());
            }
            return new Invocation("", methodSelect.toString());
        }
    }

    private record Invocation(String ownerHint, String methodName) {
    }
}
