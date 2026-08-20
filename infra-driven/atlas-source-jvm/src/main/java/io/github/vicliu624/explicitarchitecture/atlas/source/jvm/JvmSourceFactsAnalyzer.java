package io.github.vicliu624.explicitarchitecture.atlas.source.jvm;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            "Character", "Void", "Class", "Number", "Throwable", "Error", "Exception", "RuntimeException",
            "IllegalArgumentException", "Enum", "Comparable", "Iterable", "AutoCloseable");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<Path> sourceFiles = JavaSourceAnalysis.findProductionJavaSources(request);
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
            List<CompilationUnitTree> compilationUnits = new ArrayList<>();
            task.parse().forEach(compilationUnits::add);
            Trees trees = Trees.instance(task);
            Map<String, Set<String>> knownSourceTypesBySimpleName = knownSourceTypesBySimpleName(compilationUnits);
            for (CompilationUnitTree unit : compilationUnits) {
                new SourceScanner(request, unit, trees, knownSourceTypesBySimpleName).scan(unit, facts);
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

    /**
     * 仅从本轮实际解析到的顶层声明建立简单名索引，供 {@code import a.b.*} 的保守类型归一化使用。
     *
     * <p>JVM 事实和 Spring/Axon 事实必须使用同一条“源码可证明才归一”的规则；否则 Axon 能把 CommandHandler
     * 定位到完全限定签名，而 JVM 方法/参数事实仍保留同包伪类型，两者不能收敛到同一个 CodeElement。</p>
     */
    private static Map<String, Set<String>> knownSourceTypesBySimpleName(List<CompilationUnitTree> units) {
        Map<String, Set<String>> types = new HashMap<>();
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree type && !type.getSimpleName().toString().isBlank()) {
                    String qualifiedName = packageName.isEmpty()
                            ? type.getSimpleName().toString()
                            : packageName + "." + type.getSimpleName();
                    types.computeIfAbsent(type.getSimpleName().toString(), ignored -> new LinkedHashSet<>()).add(qualifiedName);
                }
            }
        }
        Map<String, Set<String>> immutable = new HashMap<>();
        types.forEach((simpleName, candidates) -> immutable.put(simpleName, Set.copyOf(candidates)));
        return Map.copyOf(immutable);
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
        private final Set<String> onDemandImportPackages;
        private final Map<String, Set<String>> knownSourceTypesBySimpleName;
        private final List<String> typeStack = new ArrayList<>();
        private String currentMethodSubject;
        private String currentMethodReturnType;

        private SourceScanner(
                AnalysisRequest request,
                CompilationUnitTree unit,
                Trees trees,
                Map<String, Set<String>> knownSourceTypesBySimpleName) {
            this.request = request;
            this.unit = unit;
            this.positions = trees.getSourcePositions();
            this.sourceFile = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            this.explicitImports = importsOf(unit);
            this.onDemandImportPackages = onDemandImportPackagesOf(unit);
            this.knownSourceTypesBySimpleName = knownSourceTypesBySimpleName;
        }

        @Override
        public Void visitClass(ClassTree node, List<SourceFact> facts) {
            String simpleName = node.getSimpleName().toString();
            typeStack.add(simpleName);
            String qualifiedName = qualifiedTypeName();
            Map<String, AtlasValue> attributes = new LinkedHashMap<>();
            attributes.put("declarationKind", AtlasValue.text(node.getKind().name()));
            if (node.getExtendsClause() != null) {
                attributes.put("extendsType", AtlasValue.text(normalizeType(node.getExtendsClause())));
            }
            List<AtlasValue> implementedTypes = node.getImplementsClause().stream()
                    .map(this::normalizeType).map(AtlasValue::text).map(AtlasValue.class::cast).toList();
            if (!implementedTypes.isEmpty()) {
                attributes.put("implementsTypes", AtlasValue.values(implementedTypes));
            }
            facts.add(fact(FactKind.SOURCE_TYPE, qualifiedName, Map.copyOf(attributes), node));
            try {
                return super.visitClass(node, facts);
            } finally {
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, List<SourceFact> facts) {
            String previousMethod = currentMethodSubject;
            String previousReturnType = currentMethodReturnType;
            String owner = qualifiedTypeName();
            String methodName = node.getName().contentEquals("<init>") ? "<init>" : node.getName().toString();
            String descriptor = methodName + "(" + node.getParameters().stream()
                    .map(VariableTree::getType)
                    .map(this::normalizeType)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + ")";
            currentMethodSubject = owner + "#" + descriptor;
            currentMethodReturnType = node.getReturnType() == null ? owner : normalizeType(node.getReturnType());
            facts.add(fact(FactKind.SOURCE_METHOD, currentMethodSubject,
                    Map.of(
                            "owner", AtlasValue.text(owner),
                            "memberDescriptor", AtlasValue.text(descriptor),
                            "returnType", AtlasValue.text(currentMethodReturnType),
                            "operationKind", AtlasValue.text(node.getReturnType() == null ? "CONSTRUCTOR" : "METHOD")), node));
            for (int index = 0; index < node.getParameters().size(); index++) {
                VariableTree parameter = node.getParameters().get(index);
                String parameterSubject = currentMethodSubject + "::parameter[" + index + "]:" + parameter.getName();
                facts.add(fact(FactKind.SOURCE_PARAMETER, parameterSubject,
                        Map.of(
                                "owner", AtlasValue.text(currentMethodSubject),
                                "parameterIndex", AtlasValue.decimal(java.math.BigDecimal.valueOf(index)),
                                "parameterName", AtlasValue.text(parameter.getName().toString()),
                                "javaType", AtlasValue.text(normalizeType(parameter.getType())),
                                "implicit", AtlasValue.bool(false)), parameter));
            }
            facts.add(fact(FactKind.SOURCE_RETURN, currentMethodSubject + "::return",
                    Map.of(
                            "owner", AtlasValue.text(currentMethodSubject),
                            "javaType", AtlasValue.text(currentMethodReturnType),
                            "constructorResult", AtlasValue.bool(node.getReturnType() == null)), node));
            try {
                return super.visitMethod(node, facts);
            } finally {
                currentMethodSubject = previousMethod;
                currentMethodReturnType = previousReturnType;
            }
        }

        @Override
        public Void visitVariable(VariableTree node, List<SourceFact> facts) {
            if (currentMethodSubject == null && !typeStack.isEmpty()) {
                String subject = qualifiedTypeName() + "#" + node.getName();
                facts.add(fact(FactKind.SOURCE_FIELD, subject,
                        Map.of("owner", AtlasValue.text(qualifiedTypeName()), "type", AtlasValue.text(normalizeType(node.getType()))), node));
            } else if (currentMethodSubject != null && node.getInitializer() != null) {
                // 方法形参没有 initializer；这里仅保留具有显式静态类型和初始化表达式的局部变量，供值流在
                // requestCommand.withServerContext(...) 等场景中保持具体命令类型。
                String subject = currentMethodSubject + "::local:" + node.getName();
                facts.add(fact(FactKind.SOURCE_LOCAL_VARIABLE, subject,
                        Map.of(
                                "owner", AtlasValue.text(currentMethodSubject),
                                "variableName", AtlasValue.text(node.getName().toString()),
                                "javaType", AtlasValue.text(normalizeType(node.getType())),
                                "initializer", AtlasValue.text(node.getInitializer().toString())), node));
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
            // 对没有显式接收者的调用（例如 send(command)）保留当前声明类型。若只保留方法名，
            // 下游在不同 Controller/Service 中遇到同名辅助方法时无法可靠地还原参数流。
            Invocation invocation = invocationOf(node.getMethodSelect(), qualifiedTypeName());
            if (currentMethodSubject != null) {
                facts.add(fact(FactKind.METHOD_CALL, currentMethodSubject + " -> " + node.getMethodSelect(),
                        Map.of(
                                "caller", AtlasValue.text(currentMethodSubject),
                                "calleeOwnerHint", AtlasValue.text(invocation.ownerHint()),
                                "calleeMethodName", AtlasValue.text(invocation.methodName()),
                                "calleeExpression", AtlasValue.text(node.getMethodSelect().toString())),
                        node));
                for (int index = 0; index < node.getArguments().size(); index++) {
                    var argument = node.getArguments().get(index);
                    Invocation argumentInvocation = argument instanceof MethodInvocationTree nestedInvocation
                            ? invocationOf(nestedInvocation.getMethodSelect(), qualifiedTypeName()) : Invocation.none();
                    facts.add(fact(FactKind.METHOD_ARGUMENT_FLOW,
                            currentMethodSubject + " -> " + node.getMethodSelect() + "::argument[" + index + "]",
                            Map.of(
                                    "caller", AtlasValue.text(currentMethodSubject),
                                    "calleeOwnerHint", AtlasValue.text(invocation.ownerHint()),
                                    "calleeExpression", AtlasValue.text(node.getMethodSelect().toString()),
                                    "calleeMethodName", AtlasValue.text(invocation.methodName()),
                                    "argumentIndex", AtlasValue.decimal(java.math.BigDecimal.valueOf(index)),
                                    "argumentExpression", AtlasValue.text(argument.toString()),
                                    // 构造表达式不是某个已有形参，但它的实例类型在 AST 中是精确可见的。把这个
                                    // 证据随参数流保留，供后续跨 helper 的 Command / 值流解释使用。
                                    "argumentStaticType", AtlasValue.text(argument instanceof NewClassTree constructed
                                            ? normalizeType(constructed.getIdentifier()) : ""),
                                    "argumentReceiverExpression", AtlasValue.text(argumentInvocation.ownerHint()),
                                    "argumentInvocationMethodName", AtlasValue.text(argumentInvocation.methodName()),
                                    "argumentInvocationArgumentCount", AtlasValue.decimal(java.math.BigDecimal.valueOf(
                                            argument instanceof MethodInvocationTree nestedInvocation
                                                    ? nestedInvocation.getArguments().size() : -1))), argument));
                }
            }
            validationFact(node, invocation, facts);
            return super.visitMethodInvocation(node, facts);
        }

        @Override
        public Void visitReturn(ReturnTree node, List<SourceFact> facts) {
            if (currentMethodSubject != null && node.getExpression() != null) {
                facts.add(fact(FactKind.METHOD_RETURN_FLOW, currentMethodSubject + "::return <- " + node.getExpression(),
                        Map.of(
                                "owner", AtlasValue.text(currentMethodSubject),
                                "returnType", AtlasValue.text(currentMethodReturnType == null ? "<inferred>" : currentMethodReturnType),
                                "expression", AtlasValue.text(node.getExpression().toString())), node));
            }
            return super.visitReturn(node, facts);
        }

        @Override
        public Void visitIf(IfTree node, List<SourceFact> facts) {
            // 显式的 “条件不满足即抛出” 是业务/安全约束的常见 Java 形态，并不一定调用
            // requireNonNull 等已知 helper。只在分支实际包含 throw 时把它归为强制 guard，避免把普通
            // 控制流误写成领域规则。
            if (currentMethodSubject != null && containsThrow(node.getThenStatement())) {
                String expression = node.getCondition().toString();
                facts.add(fact(FactKind.VALIDATION_RULE, currentMethodSubject + " :: IF_THROW_GUARD :: " + expression,
                        Map.of(
                                "owner", AtlasValue.text(currentMethodSubject),
                                "ruleType", AtlasValue.text("IF_THROW_GUARD"),
                                "expression", AtlasValue.text(expression),
                                "enforcingCall", AtlasValue.text("if (...) throw"),
                                "guardMethod", AtlasValue.text("if"),
                                "argumentCount", AtlasValue.decimal(java.math.BigDecimal.ZERO)), node));
            }
            return super.visitIf(node, facts);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, List<SourceFact> facts) {
            if (currentMethodSubject != null && node.getExpression() instanceof MethodInvocationTree invocation) {
                Invocation called = invocationOf(invocation.getMethodSelect(), qualifiedTypeName());
                if (isNormalizationMethod(called.methodName())) {
                    facts.add(fact(FactKind.VALUE_NORMALIZATION,
                            currentMethodSubject + "::" + node.getVariable() + " <- " + invocation,
                            Map.of(
                                    "owner", AtlasValue.text(currentMethodSubject),
                                    "targetExpression", AtlasValue.text(node.getVariable().toString()),
                                    "transformation", AtlasValue.text(called.methodName()),
                                    "sourceExpression", AtlasValue.text(invocation.toString())), node));
                }
            }
            return super.visitAssignment(node, facts);
        }

        private void validationFact(MethodInvocationTree node, Invocation invocation, List<SourceFact> facts) {
            String methodName = invocation.methodName();
            boolean isPatternCompile = methodName.equals("compile") && invocation.ownerHint().endsWith("Pattern");
            boolean isValidationGuard = methodName.equals("matches") || methodName.equals("requireTrue")
                    || methodName.equals("requireFalse") || methodName.equals("requireCondition")
                    || methodName.equals("requireNonBlank") || methodName.equals("requireNonNull")
                    || methodName.equals("requireMatches") || methodName.equals("requirePrefix");
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
                            "enforcingCall", AtlasValue.text(node.getMethodSelect().toString()),
                            "guardMethod", AtlasValue.text(methodName),
                            "failureMessage", AtlasValue.text(node.getArguments().size() < 2 ? ""
                                    : node.getArguments().getLast().toString()),
                            "argumentCount", AtlasValue.decimal(java.math.BigDecimal.valueOf(node.getArguments().size()))),
                    node));
        }

        private static boolean isNormalizationMethod(String methodName) {
            return methodName.equals("trim") || methodName.equals("strip") || methodName.equals("stripLeading")
                    || methodName.equals("stripTrailing") || methodName.equals("toLowerCase") || methodName.equals("toUpperCase")
                    || methodName.equals("trimToNull") || methodName.equals("trimToEmpty");
        }

        private static boolean containsThrow(Tree statement) {
            if (statement == null) {
                return false;
            }
            final boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitThrow(ThrowTree node, Void unused) {
                    found[0] = true;
                    return null;
                }
            }.scan(statement, null);
            return found[0];
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
            if (type == null) {
                return "<inferred>";
            }
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

        /** 只有按需导入的项目源码中存在唯一候选时才收敛简单名；外部 classpath 候选绝不猜测。 */
        private Optional<String> resolveImportedSourceType(String simpleName) {
            Set<String> candidates = knownSourceTypesBySimpleName.getOrDefault(simpleName, Set.of()).stream()
                    .filter(candidate -> onDemandImportPackages.contains(candidate.substring(0, candidate.lastIndexOf('.'))))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return candidates.size() == 1 ? Optional.of(candidates.iterator().next()) : Optional.empty();
        }

        private static Invocation invocationOf(ExpressionTree methodSelect, String enclosingType) {
            if (methodSelect instanceof MemberSelectTree memberSelect) {
                String receiver = memberSelect.getExpression().toString();
                return new Invocation("this".equals(receiver) ? enclosingType : receiver,
                        memberSelect.getIdentifier().toString());
            }
            if (methodSelect instanceof IdentifierTree identifier) {
                return new Invocation(enclosingType, identifier.getName().toString());
            }
            return new Invocation("", methodSelect.toString());
        }
    }

    private record Invocation(String ownerHint, String methodName) {
        private static Invocation none() {
            return new Invocation("", "");
        }
    }
}
