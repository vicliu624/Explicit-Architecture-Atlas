package io.github.vicliu624.explicitarchitecture.atlas.source.axon;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JavaSourceAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 从 Axon 源码提取命令处理、事件生产/消费、事件 payload 与 Saga 生命周期事实。
 *
 * <p>识别建立在实际 Axon 注解和 {@code AggregateLifecycle.apply(...)} 调用之上。缺少可靠类型信息、
 * 没有 {@code new Event(...)} 的动态事件或无法确认的关联不会被捏造成关系。</p>
 */
public final class AxonSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:axon-source"), "Axon 源码分析器", "命令、事件、投影与 Saga 源码事实");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<SourceFact> facts = new ArrayList<>();
        JavaSourceAnalysis.ParseOutcome outcome = JavaSourceAnalysis.inspectProductionSources(
                request, DESCRIPTOR.id().value(), unit -> new AxonScanner(unit).scan(unit.compilationUnit(), facts));
        CapabilityStatus status = statusFor(outcome, facts);
        String summary = switch (status) {
            case COMPLETE -> "已提取 " + count(facts, FactKind.EVENT_PUBLICATION) + " 个事件生产点、"
                    + count(facts, FactKind.EVENT_CONSUMPTION) + " 个事件消费点和 "
                    + count(facts, FactKind.PROCESS_START) + " 个 Saga 启动点";
            case NOT_APPLICABLE -> "项目范围内没有可证明的 Axon 命令、事件、投影或 Saga 源码事实";
            case PARTIAL -> "已部分提取 Axon 静态事实；请查看解析诊断";
            case FAILED -> "当前 JVM 未提供 JavaCompiler，无法安全解析 Axon 源码";
            default -> throw new IllegalStateException("Axon 分析器不应产生状态: " + status);
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

    private static final class AxonScanner extends TreePathScanner<Void, List<SourceFact>> {

        private final JavaSourceAnalysis.JavaSourceUnit unit;
        private final List<String> typeStack = new ArrayList<>();
        private final List<AxonTypeContext> typeContexts = new ArrayList<>();
        private final Map<String, String> fieldTypes = new HashMap<>();
        private String currentMethodSubject;

        private AxonScanner(JavaSourceAnalysis.JavaSourceUnit unit) {
            this.unit = unit;
        }

        @Override
        public Void visitClass(ClassTree node, List<SourceFact> facts) {
            typeStack.add(node.getSimpleName().toString());
            try {
                List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
                typeContexts.add(new AxonTypeContext(
                        qualifiedTypeName(),
                        hasAnnotation(annotations, "Saga", "AtlasProcessManager"),
                        hasAnnotation(annotations, "AtlasProjection"),
                        hasAnnotation(annotations, "AtlasEvent")));
                return super.visitClass(node, facts);
            } finally {
                typeContexts.removeLast();
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitVariable(VariableTree node, List<SourceFact> facts) {
            AxonTypeContext type = currentType();
            if (currentMethodSubject == null && type != null) {
                fieldTypes.put(node.getName().toString(), unit.normalizeType(node.getType()));
                if (type.isEvent()) {
                    facts.add(unit.fact(
                            FactKind.EVENT_PAYLOAD_FIELD,
                            type.qualifiedName() + "#" + node.getName(),
                            Map.of(
                                    "eventType", AtlasValue.text(type.qualifiedName()),
                                    "fieldName", AtlasValue.text(node.getName().toString()),
                                    "fieldType", AtlasValue.text(unit.normalizeType(node.getType())),
                                    "fieldRole", AtlasValue.text("UNSPECIFIED")),
                            node,
                            "领域事件的源码 payload 字段"));
                }
            }
            return super.visitVariable(node, facts);
        }

        @Override
        public Void visitMethod(MethodTree node, List<SourceFact> facts) {
            String previousMethod = currentMethodSubject;
            currentMethodSubject = qualifiedTypeName() + "#" + methodDescriptor(node);
            try {
                List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
                commandHandler(node, annotations, facts);
                eventHandler(node, annotations, facts);
                return super.visitMethod(node, facts);
            } finally {
                currentMethodSubject = previousMethod;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, List<SourceFact> facts) {
            if (currentMethodSubject != null) {
                eventPublication(node).ifPresent(publication -> facts.add(unit.fact(
                        FactKind.EVENT_PUBLICATION,
                        currentMethodSubject + " -> " + publication.eventType(),
                        Map.of(
                                "producer", AtlasValue.text(currentMethodSubject),
                                "eventType", AtlasValue.text(publication.eventType())),
                        node,
                        "通过 AggregateLifecycle.apply 产生领域事件")));
                processDispatch(node).ifPresent(dispatch -> facts.add(unit.fact(
                        FactKind.PROCESS_DISPATCH,
                        currentMethodSubject + " -> " + dispatch.commandType(),
                        Map.of(
                                "processManager", AtlasValue.text(currentType().qualifiedName()),
                                "dispatcher", AtlasValue.text(dispatch.dispatcher()),
                                "dispatchMethod", AtlasValue.text(dispatch.methodName()),
                                "caller", AtlasValue.text(currentMethodSubject),
                                "commandType", AtlasValue.text(dispatch.commandType())),
                        node,
                        "Saga 通过 CommandGateway / CommandBus 发起后续命令")));
            }
            return super.visitMethodInvocation(node, facts);
        }

        private void commandHandler(MethodTree node, List<? extends AnnotationTree> annotations, List<SourceFact> facts) {
            AnnotationTree annotation = findAnnotation(annotations, "CommandHandler").orElse(null);
            if (annotation == null || node.getParameters().isEmpty()) {
                return;
            }
            String commandType = unit.normalizeType(node.getParameters().getFirst().getType());
            facts.add(unit.fact(
                    FactKind.COMMAND_HANDLER,
                    currentMethodSubject + " handles " + commandType,
                    Map.of(
                            "handler", AtlasValue.text(currentMethodSubject),
                            "handlerType", AtlasValue.text(qualifiedTypeName()),
                            "commandType", AtlasValue.text(commandType)),
                    annotation,
                    "由 Axon @CommandHandler 声明的命令处理器"));
        }

        private void eventHandler(MethodTree node, List<? extends AnnotationTree> annotations, List<SourceFact> facts) {
            AnnotationTree annotation = findAnnotation(annotations, "SagaEventHandler")
                    .or(() -> findAnnotation(annotations, "EventSourcingHandler"))
                    .or(() -> findAnnotation(annotations, "EventHandler"))
                    .orElse(null);
            if (annotation == null || node.getParameters().isEmpty()) {
                return;
            }
            String eventType = unit.normalizeType(node.getParameters().getFirst().getType());
            AxonTypeContext type = currentType();
            String role = consumerRole(annotations, type);
            facts.add(unit.fact(
                    FactKind.EVENT_CONSUMPTION,
                    currentMethodSubject + " consumes " + eventType,
                    Map.of(
                            "consumer", AtlasValue.text(currentMethodSubject),
                            "consumerType", AtlasValue.text(type.qualifiedName()),
                            "consumerRole", AtlasValue.text(role),
                            "eventType", AtlasValue.text(eventType)),
                    annotation,
                    "由 Axon 事件处理注解声明的事件消费者"));

            if (!type.isSaga() && !hasAnnotation(annotations, "SagaEventHandler")) {
                return;
            }
            String associationProperty = findAnnotation(annotations, "SagaEventHandler")
                    .flatMap(this::associationProperty)
                    .orElse("");
            Map<String, AtlasValue> processAttributes = new HashMap<>();
            processAttributes.put("processManager", AtlasValue.text(type.qualifiedName()));
            processAttributes.put("handler", AtlasValue.text(currentMethodSubject));
            processAttributes.put("eventType", AtlasValue.text(eventType));
            if (!associationProperty.isBlank()) {
                processAttributes.put("associationProperty", AtlasValue.text(associationProperty));
            }
            if (hasAnnotation(annotations, "StartSaga")) {
                facts.add(unit.fact(FactKind.PROCESS_START, currentMethodSubject + " starts " + type.qualifiedName(),
                        processAttributes, annotation, "Saga 由事件开始"));
            } else {
                facts.add(unit.fact(FactKind.PROCESS_WAIT, currentMethodSubject + " waits " + eventType,
                        processAttributes, annotation, "Saga 等待并处理关联事件"));
            }
            if (hasAnnotation(annotations, "EndSaga")) {
                facts.add(unit.fact(FactKind.PROCESS_COMPLETION, currentMethodSubject + " completes " + type.qualifiedName(),
                        processAttributes, annotation, "Saga 在事件处理后结束"));
            }
        }

        private Optional<EventPublication> eventPublication(MethodInvocationTree invocation) {
            if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)
                    || !select.getIdentifier().contentEquals("apply")
                    || !select.getExpression().toString().endsWith("AggregateLifecycle")
                    || invocation.getArguments().isEmpty()
                    || !(invocation.getArguments().getFirst() instanceof NewClassTree event)) {
                return Optional.empty();
            }
            return Optional.of(new EventPublication(unit.normalizeType(event.getIdentifier())));
        }

        private Optional<ProcessDispatch> processDispatch(MethodInvocationTree invocation) {
            AxonTypeContext type = currentType();
            if (type == null || !type.isSaga() || !(invocation.getMethodSelect() instanceof MemberSelectTree select)
                    || !List.of("send", "sendAndWait", "dispatch").contains(select.getIdentifier().toString())
                    || invocation.getArguments().isEmpty()
                    || !(invocation.getArguments().getFirst() instanceof NewClassTree command)) {
                return Optional.empty();
            }
            String receiverType = receiverType(select.getExpression()).orElse("");
            if (!receiverType.endsWith("CommandGateway") && !receiverType.endsWith("CommandBus")) {
                return Optional.empty();
            }
            return Optional.of(new ProcessDispatch(
                    select.getExpression().toString(),
                    select.getIdentifier().toString(),
                    unit.normalizeType(command.getIdentifier())));
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

        private String consumerRole(List<? extends AnnotationTree> annotations, AxonTypeContext type) {
            if (hasAnnotation(annotations, "EventSourcingHandler")) {
                return "AGGREGATE_STATE";
            }
            if (type.isSaga() || hasAnnotation(annotations, "SagaEventHandler")) {
                return "PROCESS_MANAGER";
            }
            if (type.isProjection()) {
                return "PROJECTION";
            }
            return "EVENT_HANDLER";
        }

        private Optional<String> associationProperty(AnnotationTree annotation) {
            return annotation.getArguments().stream()
                    .filter(AssignmentTree.class::isInstance)
                    .map(AssignmentTree.class::cast)
                    .filter(assignment -> assignment.getVariable().toString().equals("associationProperty"))
                    .map(AssignmentTree::getExpression)
                    .filter(LiteralTree.class::isInstance)
                    .map(LiteralTree.class::cast)
                    .map(LiteralTree::getValue)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .findFirst();
        }

        private AxonTypeContext currentType() {
            return typeContexts.isEmpty() ? null : typeContexts.getLast();
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

        private Optional<AnnotationTree> findAnnotation(List<? extends AnnotationTree> annotations, String name) {
            return annotations.stream()
                    .filter(annotation -> unit.annotationSimpleName(annotation).equals(name))
                    .map(annotation -> (AnnotationTree) annotation)
                    .findFirst();
        }
    }

    private record AxonTypeContext(String qualifiedName, boolean isSaga, boolean isProjection, boolean isEvent) {
    }

    private record EventPublication(String eventType) {
    }

    private record ProcessDispatch(String dispatcher, String methodName, String commandType) {
    }
}
