package io.github.vicliu624.explicitarchitecture.atlas.source.spring;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
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
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JavaSourceAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 从 Spring MVC 源码提取可证明的 HTTP 端点和 Command Gateway 派发事实。
 *
 * <p>它只识别源码中实际出现的 Spring mapping 和显式 CommandGateway / CommandBus 调用；不启动 Spring、
 * 不依赖业务工程 classpath，也不会把命名相近的方法猜成 HTTP 接口或命令派发。</p>
 */
public final class SpringSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:spring-source"), "Spring 源码分析器", "Spring MVC Endpoint、调用与命令派发事实");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<SourceFact> facts = new ArrayList<>();
        JavaSourceAnalysis.ParseOutcome outcome = JavaSourceAnalysis.inspectProductionSources(
                request, DESCRIPTOR.id().value(), unit -> new SpringScanner(unit).scan(unit.compilationUnit(), facts));
        CapabilityStatus status = statusFor(outcome, facts);
        String summary = switch (status) {
            case COMPLETE -> "已提取 " + count(facts, FactKind.HTTP_ENDPOINT) + " 个 Spring HTTP 端点和 "
                    + count(facts, FactKind.COMMAND_DISPATCH) + " 个命令派发点";
            case NOT_APPLICABLE -> "项目范围内没有可证明的 Spring MVC 端点或 Command Gateway / Command Bus 派发";
            case PARTIAL -> "已部分提取 Spring 静态事实；请查看解析诊断";
            case FAILED -> "当前 JVM 未提供 JavaCompiler，无法安全解析 Spring 源码";
            default -> throw new IllegalStateException("Spring 分析器不应产生状态: " + status);
        };
        return new AnalyzerContribution(
                new CoverageReport(DESCRIPTOR.id().value(), status, request.scopeUri(), summary),
                facts.stream().sorted(Comparator.comparing(SourceFact::id)).toList(),
                List.of(),
                outcome.diagnostics());
    }

    private static CapabilityStatus statusFor(JavaSourceAnalysis.ParseOutcome outcome, List<SourceFact> facts) {
        if (!outcome.compilerAvailable()) {
            return CapabilityStatus.FAILED;
        }
        if (facts.isEmpty()) {
            return CapabilityStatus.NOT_APPLICABLE;
        }
        return outcome.hasErrors() ? CapabilityStatus.PARTIAL : CapabilityStatus.COMPLETE;
    }

    private static long count(List<SourceFact> facts, FactKind kind) {
        return facts.stream().filter(fact -> fact.kind() == kind).count();
    }

    private static final class SpringScanner extends TreePathScanner<Void, List<SourceFact>> {

        private final JavaSourceAnalysis.JavaSourceUnit unit;
        private final List<String> typeStack = new ArrayList<>();
        private final List<ControllerContext> controllerStack = new ArrayList<>();
        private final Map<String, String> fieldTypes = new HashMap<>();
        private String currentMethodSubject;

        private SpringScanner(JavaSourceAnalysis.JavaSourceUnit unit) {
            this.unit = unit;
        }

        @Override
        public Void visitClass(ClassTree node, List<SourceFact> facts) {
            typeStack.add(node.getSimpleName().toString());
            try {
                boolean controller = hasAnnotation(node.getModifiers().getAnnotations(), "RestController", "Controller");
                List<Mapping> mappings = mappings(node.getModifiers().getAnnotations());
                controllerStack.add(new ControllerContext(controller, pathsOf(mappings), qualifiedTypeName()));
                return super.visitClass(node, facts);
            } finally {
                controllerStack.removeLast();
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitVariable(VariableTree node, List<SourceFact> facts) {
            if (currentMethodSubject == null && !typeStack.isEmpty()) {
                fieldTypes.put(node.getName().toString(), unit.normalizeType(node.getType()));
            }
            return super.visitVariable(node, facts);
        }

        @Override
        public Void visitMethod(MethodTree node, List<SourceFact> facts) {
            String previousMethod = currentMethodSubject;
            String descriptor = methodDescriptor(node);
            currentMethodSubject = qualifiedTypeName() + "#" + descriptor;
            try {
                ControllerContext controller = controllerStack.isEmpty() ? ControllerContext.notController() : controllerStack.getLast();
                List<Mapping> mappings = mappings(node.getModifiers().getAnnotations());
                if (controller.isController() && !mappings.isEmpty()) {
                    for (Mapping mapping : mappings) {
                        List<String> paths = combinePaths(controller.basePaths(), mapping.paths());
                        facts.add(unit.fact(
                                FactKind.HTTP_ENDPOINT,
                                currentMethodSubject,
                                Map.of(
                                        "handler", AtlasValue.text(currentMethodSubject),
                                        "controller", AtlasValue.text(controller.typeName()),
                                        "httpMethods", values(mapping.httpMethods()),
                                        "paths", values(paths)),
                                mapping.annotation(),
                                "Spring " + String.join(",", mapping.httpMethods()) + " endpoint 由映射注解提取"));
                    }
                }
                return super.visitMethod(node, facts);
            } finally {
                currentMethodSubject = previousMethod;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, List<SourceFact> facts) {
            if (currentMethodSubject != null) {
                commandDispatch(node).ifPresent(dispatch -> facts.add(unit.fact(
                        FactKind.COMMAND_DISPATCH,
                        currentMethodSubject + " -> " + dispatch.commandType(),
                        Map.of(
                                "dispatcher", AtlasValue.text(dispatch.dispatcher()),
                                "dispatchMethod", AtlasValue.text(dispatch.methodName()),
                                "caller", AtlasValue.text(currentMethodSubject),
                                "commandType", AtlasValue.text(dispatch.commandType())),
                        node,
                        "通过 Spring CommandGateway / CommandBus 派发命令")));
            }
            return super.visitMethodInvocation(node, facts);
        }

        private Optional<CommandDispatch> commandDispatch(MethodInvocationTree invocation) {
            if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
                return Optional.empty();
            }
            String methodName = select.getIdentifier().toString();
            if (!List.of("send", "sendAndWait", "dispatch").contains(methodName) || invocation.getArguments().isEmpty()) {
                return Optional.empty();
            }
            String receiver = select.getExpression().toString();
            String receiverType = receiverType(select.getExpression()).orElse("");
            if (!receiverType.endsWith("CommandGateway") && !receiverType.endsWith("CommandBus")) {
                return Optional.empty();
            }
            ExpressionTree command = invocation.getArguments().getFirst();
            if (!(command instanceof NewClassTree newCommand)) {
                return Optional.empty();
            }
            return Optional.of(new CommandDispatch(receiver, methodName, unit.normalizeType(newCommand.getIdentifier())));
        }

        private Optional<String> receiverType(ExpressionTree expression) {
            if (expression instanceof IdentifierTree identifier) {
                return Optional.ofNullable(fieldTypes.get(identifier.getName().toString()));
            }
            if (expression instanceof MemberSelectTree memberSelect) {
                return Optional.ofNullable(fieldTypes.get(memberSelect.getIdentifier().toString()));
            }
            return Optional.empty();
        }

        private String qualifiedTypeName() {
            String nesting = String.join("$", typeStack);
            return unit.packageName().isEmpty() ? nesting : unit.packageName() + "." + nesting;
        }

        private String methodDescriptor(MethodTree node) {
            String methodName = node.getName().contentEquals("<init>") ? "<init>" : node.getName().toString();
            String parameters = node.getParameters().stream()
                    .map(VariableTree::getType)
                    .map(unit::normalizeType)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return methodName + "(" + parameters + ")";
        }

        private boolean hasAnnotation(List<? extends AnnotationTree> annotations, String... names) {
            return Stream.of(names).anyMatch(name -> annotations.stream()
                    .map(unit::annotationSimpleName)
                    .anyMatch(name::equals));
        }

        private List<Mapping> mappings(List<? extends AnnotationTree> annotations) {
            return annotations.stream()
                    .map(this::mapping)
                    .flatMap(Optional::stream)
                    .toList();
        }

        private Optional<Mapping> mapping(AnnotationTree annotation) {
            String simpleName = unit.annotationSimpleName(annotation);
            List<String> methods = switch (simpleName) {
                case "GetMapping" -> List.of("GET");
                case "PostMapping" -> List.of("POST");
                case "PutMapping" -> List.of("PUT");
                case "PatchMapping" -> List.of("PATCH");
                case "DeleteMapping" -> List.of("DELETE");
                case "RequestMapping" -> requestMethods(annotation);
                default -> List.of();
            };
            if (methods.isEmpty()) {
                return Optional.empty();
            }
            List<String> paths = annotationStrings(annotation, "path", "value");
            return Optional.of(new Mapping(methods, paths.isEmpty() ? List.of("") : paths, annotation));
        }

        private List<String> requestMethods(AnnotationTree annotation) {
            List<String> values = annotationStrings(annotation, "method");
            if (values.isEmpty()) {
                return List.of("ANY");
            }
            return values.stream()
                    .map(value -> value.substring(value.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT))
                    .toList();
        }

        private List<String> annotationStrings(AnnotationTree annotation, String... names) {
            List<String> values = new ArrayList<>();
            for (ExpressionTree argument : annotation.getArguments()) {
                if (argument instanceof AssignmentTree assignment) {
                    String name = assignment.getVariable().toString();
                    if (List.of(names).contains(name)) {
                        values.addAll(stringsOf(assignment.getExpression()));
                    }
                } else if (List.of(names).contains("value")) {
                    values.addAll(stringsOf(argument));
                }
            }
            return values.stream().filter(value -> !value.isBlank()).distinct().toList();
        }

        private List<String> stringsOf(ExpressionTree expression) {
            if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
                return List.of(value);
            }
            if (expression instanceof NewArrayTree array && array.getInitializers() != null) {
                return array.getInitializers().stream().flatMap(value -> stringsOf(value).stream()).toList();
            }
            String raw = expression.toString();
            return raw.isBlank() ? List.of() : List.of(raw);
        }

        private List<String> pathsOf(List<Mapping> mappings) {
            if (mappings.isEmpty()) {
                return List.of("");
            }
            return mappings.stream().flatMap(mapping -> mapping.paths().stream()).distinct().toList();
        }

        private List<String> combinePaths(List<String> basePaths, List<String> methodPaths) {
            return basePaths.stream().flatMap(base -> methodPaths.stream().map(method -> normalizePath(base, method)))
                    .distinct().toList();
        }

        private String normalizePath(String base, String method) {
            String joined = (base == null ? "" : base.trim()) + "/" + (method == null ? "" : method.trim());
            String normalized = joined.replaceAll("/{2,}", "/");
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized.length() > 1 && normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        }

        private AtlasValue.Values values(List<String> entries) {
            return AtlasValue.values(entries.stream().map(AtlasValue::text).map(AtlasValue.class::cast).toList());
        }
    }

    private record ControllerContext(boolean isController, List<String> basePaths, String typeName) {
        private ControllerContext {
            basePaths = List.copyOf(basePaths);
        }

        static ControllerContext notController() {
            return new ControllerContext(false, List.of(""), "");
        }
    }

    private record Mapping(List<String> httpMethods, List<String> paths, AnnotationTree annotation) {
        private Mapping {
            httpMethods = List.copyOf(httpMethods);
            paths = List.copyOf(paths);
            Objects.requireNonNull(annotation, "annotation 不能为空");
        }
    }

    private record CommandDispatch(String dispatcher, String methodName, String commandType) {
    }
}
