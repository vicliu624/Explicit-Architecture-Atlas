package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentDefinition;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionExpression;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionTerm;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTargetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataSensitivity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraintKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EnforcementLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventField;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventFieldRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ExternalBoundary;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BoundaryKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentDeclaration;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionPoint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationContract;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationFailure;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInput;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInputRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcome;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcomeKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValueCategory;
import io.github.vicliu624.explicitarchitecture.atlas.domain.TrustLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueNullability;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认的语义归一化器：显式意图决定业务名称和职责，源码事实决定可证明的实现与调用证据。
 *
 * <p>它绝不把一个未安装分析器的空结果解读为“不存在事件、投影或 Saga”；覆盖状态由 Package Manifest 单独表达。
 * 无法可靠匹配的调用同样不会臆造图边。</p>
 */
public final class DefaultDesignAtlasResolver implements DesignAtlasResolver {

    @Override
    public DesignAtlasResolution resolve(FactBundle facts, IntentBundle intents, ContentBundle content) {
        Objects.requireNonNull(facts, "facts 不能为空");
        Objects.requireNonNull(intents, "intents 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        ResolutionState state = new ResolutionState(facts);
        state.addSourceElements();
        state.addOperationContractsAndValueFlows();
        state.addIntentAssets(intents.declarations());
        state.addFrameworkSemantics();
        state.addComponentRuntimeSemantics();
        state.addDerivedValidationConstraints();
        state.addResolvedCalls();
        return new DesignAtlasResolution(
                new AtlasGraph(List.copyOf(state.nodes.values()), List.copyOf(state.relations.values())),
                state.diagnostics.stream().sorted(Comparator.comparing(AtlasDiagnostic::id)).toList());
    }

    private static final class ResolutionState {

        private final FactBundle facts;
        private final Map<AtlasId, ArchitectureAsset> nodes = new LinkedHashMap<>();
        private final Map<AtlasId, AtlasRelation> relations = new LinkedHashMap<>();
        private final List<AtlasDiagnostic> diagnostics = new ArrayList<>();
        private final Map<String, List<SourceFact>> factsBySubject;
        private final Map<String, List<String>> directSupertypes;
        private final Map<String, AtlasId> persistenceEntityTargets = new LinkedHashMap<>();
        private final Map<String, AtlasId> persistenceTableTargets = new LinkedHashMap<>();

        private ResolutionState(FactBundle facts) {
            this.facts = facts;
            this.factsBySubject = facts.facts().stream().collect(Collectors.groupingBy(SourceFact::subject));
            this.directSupertypes = directSupertypes(facts.facts());
        }

        private void addSourceElements() {
            for (SourceFact fact : facts.facts()) {
                CodeElementKind kind = switch (fact.kind()) {
                    case SOURCE_TYPE -> CodeElementKind.TYPE;
                    case SOURCE_FIELD -> CodeElementKind.FIELD;
                    case SOURCE_METHOD -> CodeElementKind.METHOD;
                    case SOURCE_PARAMETER -> CodeElementKind.PARAMETER;
                    case MAVEN_MODULE, MAVEN_DEPENDENCY -> CodeElementKind.CONFIGURATION;
                    default -> null;
                };
                if (kind != null) {
                    upsertCodeElement(fact.subject(), kind, fact.evidence(), fact.evidence().getFirst().locator());
                }
            }
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.SOURCE_TYPE) {
                    continue;
                }
                AtlasId subtype = ensureCodeElement(fact.subject(), CodeElementKind.TYPE, fact.evidence());
                text(fact.attributes(), "extendsType").ifPresent(supertype -> {
                    AtlasId target = ensureCodeElement(supertype, CodeElementKind.TYPE, fact.evidence());
                    addRelation(RelationKind.IMPLEMENTS, subtype, target, fact.evidence(),
                            Map.of("inheritanceKind", AtlasValue.text("EXTENDS")),
                            "type-extends:" + subtype + ":" + target + ":" + fact.id());
                });
                for (String supertype : values(fact.attributes(), "implementsTypes")) {
                    AtlasId target = ensureCodeElement(supertype, CodeElementKind.TYPE, fact.evidence());
                    addRelation(RelationKind.IMPLEMENTS, subtype, target, fact.evidence(),
                            Map.of("inheritanceKind", AtlasValue.text("IMPLEMENTS")),
                            "type-implements:" + subtype + ":" + target + ":" + fact.id());
                }
            }
        }

        /**
         * 将 JVM 参数、返回、调用参数和归一化事实提升为可查询的操作契约与语义值流。
         *
         * <p>只对语法已证明且可唯一关联的路径建图；复杂表达式或多态调用仍作为原始 Fact 保留，不伪造成
         * 唯一参数绑定。</p>
         */
        private void addOperationContractsAndValueFlows() {
            Map<String, AtlasId> operationIds = new LinkedHashMap<>();
            Map<String, AtlasId> parameterValues = new LinkedHashMap<>();
            Map<String, AtlasId> returnValues = new LinkedHashMap<>();
            Map<String, List<AtlasId>> parametersByMethod = new LinkedHashMap<>();

            List<SourceFact> methods = facts.facts().stream()
                    .filter(fact -> fact.kind() == FactKind.SOURCE_METHOD)
                    .toList();
            // 真实工程会有数万条源码事实。方法契约归一化原先会为每一个方法重新扫描全量事实寻找参数，
            // 并为每一个实参流重新扫描所有操作名；这会使全量抽取退化为 O(methods × facts)。
            // 这些索引只改变查找方式，不改变事实的原有顺序、重载的歧义语义或最终图的身份。
            Map<String, List<SourceFact>> parametersByOwner = facts.facts().stream()
                    .filter(fact -> fact.kind() == FactKind.SOURCE_PARAMETER)
                    .collect(Collectors.groupingBy(
                            fact -> text(fact.attributes(), "owner").orElse(""),
                            LinkedHashMap::new,
                            Collectors.toList()));
            Map<String, List<SourceFact>> localsByOwner = facts.facts().stream()
                    .filter(fact -> fact.kind() == FactKind.SOURCE_LOCAL_VARIABLE)
                    .collect(Collectors.groupingBy(
                            fact -> text(fact.attributes(), "owner").orElse(""),
                            LinkedHashMap::new,
                            Collectors.toList()));
            Map<String, List<String>> methodsByName = methods.stream().collect(Collectors.groupingBy(
                    method -> methodName(method.subject()), LinkedHashMap::new,
                    Collectors.mapping(SourceFact::subject, Collectors.toList())));
            Map<String, SourceFact> methodsBySubject = methods.stream().collect(Collectors.toMap(
                    SourceFact::subject, method -> method, (first, ignored) -> first, LinkedHashMap::new));
            for (SourceFact method : methods) {
                String methodSubject = method.subject();
                AtlasId methodCodeId = ensureCodeElement(methodSubject, CodeElementKind.METHOD, method.evidence());
                List<SourceFact> parameters = parametersByOwner.getOrDefault(methodSubject, List.of()).stream()
                        .sorted(Comparator.comparingInt(fact -> integer(fact.attributes(), "parameterIndex", Integer.MAX_VALUE)))
                        .toList();
                List<OperationInput> inputs = parameters.stream()
                        .map(parameter -> new OperationInput(
                                text(parameter.attributes(), "parameterName").orElse(simpleName(parameter.subject())),
                                text(parameter.attributes(), "javaType").orElse("<inferred>"),
                                OperationInputRole.BUSINESS_INPUT,
                                false,
                                ValueNullability.UNKNOWN,
                                "由 Java 方法参数提取；业务语义待注解或文档补充"))
                        .toList();
                String returnType = text(method.attributes(), "returnType").orElse("<inferred>");
                OperationKind operationKind = "CONSTRUCTOR".equals(text(method.attributes(), "operationKind").orElse(""))
                        ? OperationKind.CONSTRUCTOR : OperationKind.METHOD;
                AtlasId operationId = operationId(methodSubject);
                OperationContract operation = new OperationContract(
                        new AssetMetadata(operationId, methodSubject, method.attributes(), method.evidence()),
                        operationKind,
                        methodSubject,
                        method.evidence().getFirst().locator(),
                        inputs,
                        List.of(new OperationOutcome(OperationOutcomeKind.RETURN_VALUE, "return", returnType, "",
                                operationKind == OperationKind.CONSTRUCTOR ? "成功构造的实例" : "Java 返回值；副作用由事实补充")),
                        List.of());
                putNode(operation);
                operationIds.put(methodSubject, operationId);
                addRelation(RelationKind.REPRESENTS, operationId, methodCodeId, method.evidence(),
                        "operation-represents-method:" + operationId + ":" + methodCodeId);

                List<AtlasId> parameterIds = new ArrayList<>();
                for (SourceFact parameter : parameters) {
                    String parameterName = text(parameter.attributes(), "parameterName").orElse(simpleName(parameter.subject()));
                    String javaType = text(parameter.attributes(), "javaType").orElse("<inferred>");
                    AtlasId parameterCodeId = ensureCodeElement(parameter.subject(), CodeElementKind.PARAMETER, parameter.evidence());
                    AtlasId valueId = semanticValueId("parameter:" + parameter.subject());
                    SemanticValue value = new SemanticValue(
                            new AssetMetadata(valueId, parameterName,
                                    Map.of(
                                            "operation", AtlasValue.text(methodSubject),
                                            "parameterIndex", AtlasValue.decimal(java.math.BigDecimal.valueOf(
                                                    integer(parameter.attributes(), "parameterIndex", -1)))),
                                    parameter.evidence()),
                            SemanticValueCategory.CUSTOM, javaType, ValueCardinality.SINGLE,
                            ValueNullability.UNKNOWN, TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "RAW");
                    putNode(value);
                    parameterValues.put(methodSubject + "::" + parameterName, valueId);
                    parameterIds.add(valueId);
                    addRelation(RelationKind.ACCEPTS, operationId, valueId, parameter.evidence(),
                            "operation-accepts-value:" + operationId + ":" + valueId);
                    addRelation(RelationKind.REPRESENTS, valueId, parameterCodeId, parameter.evidence(),
                            "semantic-value-represents-parameter:" + valueId + ":" + parameterCodeId);
                }
                parametersByMethod.put(methodSubject, List.copyOf(parameterIds));

                for (SourceFact local : localsByOwner.getOrDefault(methodSubject, List.of())) {
                    String localName = text(local.attributes(), "variableName").orElse(simpleName(local.subject()));
                    String javaType = text(local.attributes(), "javaType").orElse("<inferred>");
                    AtlasId valueId = semanticValueId("local:" + local.subject());
                    putNode(new SemanticValue(
                            new AssetMetadata(valueId, localName,
                                    Map.of(
                                            "operation", AtlasValue.text(methodSubject),
                                            "initializer", AtlasValue.text(text(local.attributes(), "initializer").orElse("")),
                                            "localDeclaration", AtlasValue.bool(true)),
                                    local.evidence()),
                            SemanticValueCategory.CUSTOM, javaType, ValueCardinality.SINGLE,
                            ValueNullability.UNKNOWN, TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "LOCAL_DECLARATION"));
                    parameterValues.put(methodSubject + "::" + localName, valueId);
                }

                AtlasId returnValueId = semanticValueId("return:" + methodSubject);
                SemanticValue returnValue = new SemanticValue(
                        new AssetMetadata(returnValueId, operationKind == OperationKind.CONSTRUCTOR ? "constructed-instance" : "return",
                                Map.of("operation", AtlasValue.text(methodSubject)), method.evidence()),
                        operationKind == OperationKind.CONSTRUCTOR ? SemanticValueCategory.VALUE_OBJECT : SemanticValueCategory.CUSTOM,
                        returnType, ValueCardinality.SINGLE, ValueNullability.UNKNOWN, TrustLevel.UNKNOWN,
                        DataSensitivity.UNKNOWN, operationKind == OperationKind.CONSTRUCTOR ? "CONSTRUCTED" : "RETURNED");
                putNode(returnValue);
                returnValues.put(methodSubject, returnValueId);
                addRelation(RelationKind.RETURNS, operationId, returnValueId, method.evidence(),
                        "operation-returns-value:" + operationId + ":" + returnValueId);
            }

            addReturnFlows(returnValues, parameterValues);
            addArgumentBindings(methodsByName, methodsBySubject, parametersByMethod, parameterValues);
            addNormalizationFlows(parameterValues);
        }

        private void addReturnFlows(Map<String, AtlasId> returnValues, Map<String, AtlasId> parameterValues) {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.METHOD_RETURN_FLOW) {
                    continue;
                }
                String method = text(fact.attributes(), "owner").orElse("");
                AtlasId source = parameterValues.get(method + "::" + text(fact.attributes(), "expression").orElse(""));
                AtlasId target = returnValues.get(method);
                if (source != null && target != null) {
                    addRelation(RelationKind.RETURN_FLOWS_TO, source, target, fact.evidence(), fact.attributes(),
                            "parameter-flows-to-return:" + source + ":" + target + ":" + fact.id());
                }
            }
        }

        private void addArgumentBindings(
                Map<String, List<String>> methodsByName,
                Map<String, SourceFact> methodsBySubject,
                Map<String, List<AtlasId>> parametersByMethod,
                Map<String, AtlasId> parameterValues) {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.METHOD_ARGUMENT_FLOW) {
                    continue;
                }
                String caller = text(fact.attributes(), "caller").orElse("");
                String argumentExpression = text(fact.attributes(), "argumentExpression").orElse("");
                AtlasId sourceValue = parameterValues.get(caller + "::" + argumentExpression);
                if (sourceValue == null) {
                    sourceValue = constructedArgumentValue(fact, argumentExpression);
                }
                if (sourceValue == null) {
                    sourceValue = staticallyTypedInvocationArgumentValue(
                            fact, caller, methodsByName, methodsBySubject, parameterValues);
                }
                if (sourceValue == null) {
                    continue;
                }
                String methodName = text(fact.attributes(), "calleeMethodName").orElse("");
                List<String> candidates = argumentBindingCandidates(methodsByName, caller,
                        text(fact.attributes(), "calleeOwnerHint").orElse(""), methodName);
                int index = integer(fact.attributes(), "argumentIndex", -1);
                if (candidates.size() == 1 && index >= 0) {
                    List<AtlasId> targetParameters = parametersByMethod.getOrDefault(candidates.getFirst(), List.of());
                    if (index < targetParameters.size()) {
                        AtlasId targetValue = targetParameters.get(index);
                        addRelation(RelationKind.ARGUMENT_BINDS_TO, sourceValue, targetValue, fact.evidence(), fact.attributes(),
                                "argument-binds-to:" + sourceValue + ":" + targetValue + ":" + fact.id());
                    }
                } else if (candidates.size() > 1) {
                    diagnostics.add(diagnostic("AMBIGUOUS_ARGUMENT_BINDING",
                            "无法唯一解析参数绑定目标: " + fact.subject(), fact.evidence().getFirst().locator()));
                }
            }
        }

        /**
         * 对 {@code command.withServerContext(...)} 这类 fluent 实参，不把接收者静态类型直接当作返回值。
         * 只有调用点接收者可定位为本方法参数/局部变量，且项目源码中存在唯一同名同参数个数的方法声明并明确
         * 返回具体类型时，才建立该中间值；否则保持原始事实而不虚构 Command 分派。
         */
        private AtlasId staticallyTypedInvocationArgumentValue(
                SourceFact fact,
                String caller,
                Map<String, List<String>> methodsByName,
                Map<String, SourceFact> methodsBySubject,
                Map<String, AtlasId> valuesByMethodAndName) {
            String receiver = text(fact.attributes(), "argumentReceiverExpression").orElse("").trim();
            String invokedMethod = text(fact.attributes(), "argumentInvocationMethodName").orElse("").trim();
            int argumentCount = integer(fact.attributes(), "argumentInvocationArgumentCount", -1);
            if (receiver.isBlank() || invokedMethod.isBlank() || argumentCount < 0 || !isSimpleIdentifier(receiver)) {
                return null;
            }
            AtlasId receiverValueId = valuesByMethodAndName.get(caller + "::" + receiver);
            ArchitectureAsset receiverAsset = receiverValueId == null ? null : nodes.get(receiverValueId);
            if (!(receiverAsset instanceof SemanticValue receiverValue)) {
                return null;
            }
            List<SourceFact> candidates = methodsByName.getOrDefault(invokedMethod, List.of()).stream()
                    .filter(methodsBySubject::containsKey)
                    .map(methodsBySubject::get)
                    .filter(method -> ownerMatches(methodOwner(method.subject()), receiverValue.javaRepresentation()))
                    .filter(method -> methodParameterCount(method.subject()) == argumentCount)
                    .toList();
            if (candidates.size() != 1) {
                return null;
            }
            SourceFact invoked = candidates.getFirst();
            String returnType = text(invoked.attributes(), "returnType").orElse("<inferred>");
            if (returnType.isBlank() || returnType.equals("<inferred>") || returnType.equals("void")) {
                return null;
            }
            AtlasId valueId = semanticValueId("static-invocation-argument:" + fact.id());
            if (!nodes.containsKey(valueId)) {
                String expression = text(fact.attributes(), "argumentExpression").orElse("");
                putNode(new SemanticValue(new AssetMetadata(valueId, expression, Map.of(
                        "operation", AtlasValue.text(caller),
                        "expression", AtlasValue.text(expression),
                        "invokedMethod", AtlasValue.text(invoked.subject()),
                        "resolution", AtlasValue.text("UNIQUE_SOURCE_RETURN_TYPE")), fact.evidence()),
                        SemanticValueCategory.CUSTOM, returnType, ValueCardinality.SINGLE, ValueNullability.UNKNOWN,
                        TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "STATIC_INVOCATION_RETURN"));
                addRelation(RelationKind.DERIVES_FROM, receiverValue.id(), valueId, fact.evidence(),
                        "static-invocation-return-derives-from:" + receiverValue.id() + ":" + valueId + ":" + fact.id());
            }
            return valueId;
        }

        /**
         * {@code new CreateOrderCommand(...)} 这类调用实参没有对应的调用方形参，不能因为不存在变量名绑定而丢失。
         * JVM AST 已给出构造类型时，将其建为有源码证据的临时值节点；其它复杂表达式仍不尝试推断。
         */
        private AtlasId constructedArgumentValue(SourceFact fact, String expression) {
            String staticType = text(fact.attributes(), "argumentStaticType").orElse("").trim();
            if (staticType.isBlank() || staticType.equals("<inferred>")) {
                return null;
            }
            AtlasId id = semanticValueId("constructed-argument:" + fact.id());
            if (!nodes.containsKey(id)) {
                Map<String, AtlasValue> attributes = new LinkedHashMap<>();
                attributes.put("constructionType", AtlasValue.text(staticType));
                attributes.put("expression", AtlasValue.text(expression));
                attributes.put("caller", AtlasValue.text(text(fact.attributes(), "caller").orElse("")));
                attributes.put("sourceFact", AtlasValue.text(fact.id().value()));
                putNode(new SemanticValue(new AssetMetadata(id, expression, Map.copyOf(attributes), fact.evidence()),
                        SemanticValueCategory.CUSTOM, staticType, ValueCardinality.SINGLE, ValueNullability.UNKNOWN,
                        TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "CONSTRUCTED_ARGUMENT"));
            }
            return id;
        }

        /**
         * 参数绑定必须先遵从调用点的接收者/当前声明类型。只按方法名归并会把同名 Controller
         * 辅助方法的参数流混为一谈，进而断开具体 Command 到网关分发的证据链。
         *
         * <p>旧包没有 {@code calleeOwnerHint} 时保留保守的全局唯一回退；多候选绝不猜测。</p>
         */
        private static List<String> argumentBindingCandidates(
                Map<String, List<String>> methodsByName,
                String caller,
                String ownerHint,
                String methodName) {
            List<String> candidates = methodsByName.getOrDefault(methodName, List.of());
            String expectedOwner = ownerHint == null || ownerHint.isBlank() ? methodOwner(caller) : ownerHint;
            if (expectedOwner.isBlank()) {
                return candidates;
            }
            List<String> scoped = candidates.stream()
                    .filter(candidate -> ownerMatches(methodOwner(candidate), expectedOwner))
                    .toList();
            return scoped.isEmpty() ? candidates : scoped;
        }

        private static boolean ownerMatches(String candidateOwner, String ownerHint) {
            if (candidateOwner.equals(ownerHint)) {
                return true;
            }
            String candidateSimpleName = simpleName(candidateOwner);
            String hintSimpleName = simpleName(ownerHint);
            return !candidateSimpleName.isBlank() && candidateSimpleName.equals(hintSimpleName)
                    && (!ownerHint.contains(".") || !candidateOwner.contains("."));
        }

        private void addNormalizationFlows(Map<String, AtlasId> parameterValues) {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.VALUE_NORMALIZATION) {
                    continue;
                }
                String method = text(fact.attributes(), "owner").orElse("");
                String targetExpression = text(fact.attributes(), "targetExpression").orElse("");
                AtlasId source = parameterValues.get(method + "::" + targetExpression);
                if (source == null) {
                    continue;
                }
                AtlasId normalized = semanticValueId("normalized:" + method + ":" + targetExpression + ":" + fact.id());
                SemanticValue value = new SemanticValue(
                        new AssetMetadata(normalized, targetExpression + " (normalized)", fact.attributes(), fact.evidence()),
                        SemanticValueCategory.CUSTOM, "<inferred>", ValueCardinality.SINGLE, ValueNullability.UNKNOWN,
                        TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "NORMALIZED");
                putNode(value);
                addRelation(RelationKind.NORMALIZES, source, normalized, fact.evidence(), fact.attributes(),
                        "normalizes-value:" + source + ":" + normalized + ":" + fact.id());
            }
        }

        /**
         * 将 Spring/Axon 已证明的源码事实归一为最终 Atlas 节点与关系。
         *
         * <p>框架分析器只提供证据充分的原始事实；这里不根据包名或命名约定推断端点、事件或 Saga。
         * 显式意图存在时它决定业务名称与用途，不存在时才生成带 {@code derived:} 身份的技术语义节点。</p>
         */
        private void addFrameworkSemantics() {
            addHttpEndpoints();
            addEventPayloadFields();
            addEventPublications();
            addEventConsumptions();
            addCommandHandlers();
            addCommandDispatches();
            addProcessLifecycle();
            addProcessDispatches();
            addJpaPersistenceTargets();
            addProjectionWrites();
        }

        /** 将 Spring/DI 适配器的 Bean、条件、注入与 AOP 声明归一为可场景化的组件图。 */
        private void addComponentRuntimeSemantics() {
            Map<String, List<SourceFact>> conditionsByBean = facts.facts().stream()
                    .filter(fact -> fact.kind() == FactKind.BEAN_CONDITION)
                    .collect(Collectors.groupingBy(fact -> text(fact.attributes(), "beanName").orElse("")));
            Map<String, List<AtlasId>> componentsByBeanName = new LinkedHashMap<>();
            List<ComponentDefinition> components = new ArrayList<>();
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.BEAN_DEFINITION) {
                    continue;
                }
                String beanName = text(fact.attributes(), "beanName").orElse(simpleName(fact.subject()));
                String declaredType = text(fact.attributes(), "declaredType").orElse("java.lang.Object");
                String candidateType = text(fact.attributes(), "candidateType").orElse(declaredType);
                List<SourceFact> conditions = conditionsByBean.getOrDefault(beanName, List.of());
                ConditionExpression condition = conditionExpression(fact, conditions);
                AtlasId componentId = componentId(beanName, candidateType, fact.id());
                ComponentDefinition component = new ComponentDefinition(
                        new AssetMetadata(componentId, beanName, fact.attributes(), fact.evidence()),
                        componentKind(text(fact.attributes(), "componentKind").orElse("CUSTOM")),
                        beanName,
                        declaredType,
                        candidateType,
                        componentScope(text(fact.attributes(), "scope").orElse("UNKNOWN")),
                        booleanValue(fact.attributes(), "primary", false),
                        values(fact.attributes(), "qualifiers"),
                        condition);
                putNode(component);
                components.add(component);
                componentsByBeanName.computeIfAbsent(beanName, ignored -> new ArrayList<>()).add(componentId);
                AtlasId code = ensureCodeElement(candidateType, CodeElementKind.TYPE, fact.evidence());
                addRelation(RelationKind.DEFINES_BEAN, componentId, code, fact.evidence(), fact.attributes(),
                        "component-defines-bean:" + componentId + ":" + code + ":" + fact.id());
                for (SourceFact conditionFact : conditions) {
                    if (!"PROPERTY".equals(text(conditionFact.attributes(), "conditionKind").orElse(""))) {
                        continue;
                    }
                    String propertyKey = text(conditionFact.attributes(), "conditionSubject").orElse("");
                    if (propertyKey.isBlank()) {
                        continue;
                    }
                    ConfigurationProperty property = configurationProperty(propertyKey, "", conditionFact.evidence());
                    addRelation(RelationKind.ACTIVATES_WHEN, componentId, property.id(), conditionFact.evidence(),
                            conditionFact.attributes(), "component-activates-when-property:" + componentId + ":" + property.id()
                                    + ":" + conditionFact.id());
                }
            }

            addConfigurationProperties();
            addInjectionPoints(components, componentsByBeanName);
            addAdvisorRelations(components);
        }

        private ConditionExpression conditionExpression(SourceFact bean, List<SourceFact> conditions) {
            if (conditions.isEmpty()) {
                return ConditionExpression.always();
            }
            List<ConditionTerm> terms = conditions.stream()
                    .map(fact -> new ConditionTerm(
                            text(fact.attributes(), "conditionKind").orElse("CUSTOM"),
                            text(fact.attributes(), "conditionSubject").orElse(fact.subject()),
                            text(fact.attributes(), "operator").orElse("MATCHES"),
                            text(fact.attributes(), "expectedValue").orElse("")))
                    .toList();
            String canonical = conditions.stream().map(fact -> text(fact.attributes(), "canonical").orElse(fact.subject()))
                    .collect(Collectors.joining(" AND "));
            return new ConditionExpression(canonical,
                    "ACTIVE".equals(text(bean.attributes(), "conditionStatus").orElse("POSSIBLE"))
                            ? ConditionStatus.ACTIVE : ConditionStatus.POSSIBLE,
                    terms);
        }

        private void addConfigurationProperties() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.CONFIGURATION_PROPERTY) {
                    continue;
                }
                String key = text(fact.attributes(), "propertyKey").orElse(fact.subject());
                ConfigurationProperty property = configurationProperty(key,
                        text(fact.attributes(), "declaredDefault").orElse(""),
                        booleanValue(fact.attributes(), "declaredDefaultRedacted", false) ? DataSensitivity.SECRET : DataSensitivity.UNKNOWN,
                        fact.evidence());
                text(fact.attributes(), "owner").ifPresent(owner -> {
                    AtlasId code = ensureCodeElement(owner, CodeElementKind.CONFIGURATION, fact.evidence());
                    addRelation(RelationKind.READS_FROM, code, property.id(), fact.evidence(), fact.attributes(),
                            "code-reads-configuration:" + code + ":" + property.id() + ":" + fact.id());
                });
            }
        }

        private ConfigurationProperty configurationProperty(String key, String declaredDefault, List<Evidence> evidence) {
            return configurationProperty(key, declaredDefault, DataSensitivity.UNKNOWN, evidence);
        }

        private ConfigurationProperty configurationProperty(
                String key,
                String declaredDefault,
                DataSensitivity sensitivity,
                List<Evidence> evidence) {
            AtlasId id = AtlasId.of("derived:configuration-property:" + encode(key));
            ArchitectureAsset existing = nodes.get(id);
            if (existing instanceof ConfigurationProperty property) {
                DataSensitivity effectiveSensitivity = property.sensitivity() == DataSensitivity.SECRET || sensitivity == DataSensitivity.SECRET
                        ? DataSensitivity.SECRET : property.sensitivity();
                String effectiveDefault = effectiveSensitivity == DataSensitivity.SECRET ? ""
                        : property.declaredDefault().isBlank() ? declaredDefault : property.declaredDefault();
                if (effectiveSensitivity != property.sensitivity() || !effectiveDefault.equals(property.declaredDefault())) {
                    Map<String, AtlasValue> attributes = new LinkedHashMap<>(property.attributes());
                    attributes.put("propertyKey", AtlasValue.text(key));
                    attributes.put("declaredDefaultRedacted", AtlasValue.bool(effectiveSensitivity == DataSensitivity.SECRET));
                    property = new ConfigurationProperty(
                            new AssetMetadata(id, key, Map.copyOf(attributes), mergeEvidence(property.evidence(), evidence)),
                            key, effectiveDefault, effectiveSensitivity);
                    nodes.put(id, property);
                }
                return property;
            }
            ConfigurationProperty property = new ConfigurationProperty(
                    new AssetMetadata(id, key, Map.of(
                            "propertyKey", AtlasValue.text(key),
                            "declaredDefaultRedacted", AtlasValue.bool(sensitivity == DataSensitivity.SECRET)), evidence),
                    key, sensitivity == DataSensitivity.SECRET ? "" : declaredDefault, sensitivity);
            putNode(property);
            return property;
        }

        private void addInjectionPoints(
                List<ComponentDefinition> components,
                Map<String, List<AtlasId>> componentsByBeanName) {
            Map<String, List<ComponentDefinition>> componentsByAssignableType = componentsByAssignableType(components);
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.BEAN_INJECTION) {
                    continue;
                }
                String ownerBeanName = text(fact.attributes(), "ownerBeanName").orElse("");
                List<AtlasId> ownerCandidates = componentsByBeanName.getOrDefault(ownerBeanName, List.of());
                if (ownerCandidates.size() != 1) {
                    diagnostics.add(diagnostic("UNRESOLVED_INJECTION_OWNER",
                            "无法唯一关联注入点所属 Bean: " + fact.subject(), fact.evidence().getFirst().locator()));
                    continue;
                }
                AtlasId ownerId = ownerCandidates.getFirst();
                String requestedType = text(fact.attributes(), "requestedType").orElse("java.lang.Object");
                AtlasId injectionId = AtlasId.of("derived:injection:" + encode(fact.id().value()));
                InjectionPoint injection = new InjectionPoint(
                        new AssetMetadata(injectionId, text(fact.attributes(), "memberName").orElse(fact.subject()),
                                fact.attributes(), fact.evidence()),
                        ownerId,
                        requestedType,
                        injectionKind(text(fact.attributes(), "injectionKind").orElse("CUSTOM")),
                        injectionCardinality(text(fact.attributes(), "cardinality").orElse("UNKNOWN")),
                        booleanValue(fact.attributes(), "required", true),
                        values(fact.attributes(), "qualifiers"));
                putNode(injection);
                addRelation(RelationKind.REQUESTS_BEAN, ownerId, injectionId, fact.evidence(), fact.attributes(),
                        "component-requests-injection:" + ownerId + ":" + injectionId);
                componentsByAssignableType.getOrDefault(simpleName(normalizeTypeName(requestedType)), List.of()).forEach(candidate ->
                        addRelation(RelationKind.RESOLVES_TO, injectionId, candidate.id(),
                                mergeEvidence(fact.evidence(), candidate.evidence()),
                                Map.of("resolutionStatus", AtlasValue.text("POSSIBLE")),
                                "injection-resolves-to-candidate:" + injectionId + ":" + candidate.id()));
            }
        }

        /**
         * 将每个组件的声明类型、候选类型及源码已证明的所有父类型预先反转为索引。注入点解析因此只需一次
         * Map 查找；不再让每个注入点对每个组件反复递归遍历整个类型表。
         *
         * <p>索引键故意采用简单类型名，保持此前 {@code sameType} 对缺少完整类路径的语法事实所采用的保守
         * 回退语义：同名类型的多个来源均会作为候选保留，而不会被错误地收敛成唯一实现。</p>
         */
        private Map<String, List<ComponentDefinition>> componentsByAssignableType(List<ComponentDefinition> components) {
            Map<String, List<ComponentDefinition>> result = new LinkedHashMap<>();
            for (ComponentDefinition component : components) {
                Set<String> assignableTypes = new LinkedHashSet<>();
                collectAssignableTypes(component.declaredType(), new LinkedHashSet<>(), assignableTypes);
                collectAssignableTypes(component.candidateType(), new LinkedHashSet<>(), assignableTypes);
                for (String type : assignableTypes) {
                    result.computeIfAbsent(type, ignored -> new ArrayList<>()).add(component);
                }
            }
            result.replaceAll((ignored, candidates) -> candidates.stream().distinct().toList());
            return Map.copyOf(result);
        }

        private void collectAssignableTypes(String type, Set<String> visited, Set<String> assignableTypes) {
            String normalized = normalizeTypeName(type);
            if (normalized.isBlank() || !visited.add(normalized)) {
                return;
            }
            String simple = simpleName(normalized);
            assignableTypes.add(simple);
            for (String supertype : directSupertypes.getOrDefault(simple, List.of())) {
                collectAssignableTypes(supertype, visited, assignableTypes);
            }
        }

        private void addAdvisorRelations(List<ComponentDefinition> components) {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.AOP_ADVISOR) {
                    continue;
                }
                String target = text(fact.attributes(), "target").orElse("");
                AtlasId targetId = target.contains("#") ? operationId(target) : components.stream()
                        .filter(component -> component.candidateType().equals(target))
                        .map(ComponentDefinition::id).findFirst().orElse(null);
                if (targetId == null || !nodes.containsKey(targetId)) {
                    continue;
                }
                String advisor = text(fact.attributes(), "advisor").orElse("AOP");
                AtlasId advisorId = AtlasId.of("derived:advisor:" + encode(advisor));
                if (!nodes.containsKey(advisorId)) {
                    putNode(new CodeElement(new AssetMetadata(advisorId, advisor, Map.of(), fact.evidence()),
                            CodeElementKind.CONFIGURATION, advisor, fact.evidence().getFirst().locator()));
                }
                addRelation(RelationKind.INTERCEPTS, advisorId, targetId, fact.evidence(), fact.attributes(),
                        "advisor-intercepts-target:" + advisorId + ":" + targetId + ":" + fact.id());
            }
        }

        private static String normalizeTypeName(String type) {
            String normalized = type == null ? "" : type.trim();
            int genericStart = normalized.indexOf('<');
            return genericStart >= 0 ? normalized.substring(0, genericStart) : normalized;
        }

        private static Map<String, List<String>> directSupertypes(List<SourceFact> sourceFacts) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (SourceFact fact : sourceFacts) {
                if (fact.kind() != FactKind.SOURCE_TYPE) {
                    continue;
                }
                List<String> supertypes = Stream.concat(
                                text(fact.attributes(), "extendsType").stream(),
                                values(fact.attributes(), "implementsTypes").stream())
                        .map(ResolutionState::normalizeTypeName).filter(type -> !type.isBlank()).distinct().sorted().toList();
                if (!supertypes.isEmpty()) {
                    result.computeIfAbsent(simpleName(normalizeTypeName(fact.subject())), ignored -> new ArrayList<>())
                            .addAll(supertypes);
                }
            }
            result.replaceAll((ignored, supertypes) -> supertypes.stream().distinct().sorted().toList());
            return Map.copyOf(result);
        }

        private static ComponentKind componentKind(String value) {
            try {
                return ComponentKind.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return ComponentKind.CUSTOM;
            }
        }

        private static ComponentScope componentScope(String value) {
            try {
                return ComponentScope.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return ComponentScope.CUSTOM;
            }
        }

        private static InjectionKind injectionKind(String value) {
            try {
                return InjectionKind.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return InjectionKind.CUSTOM;
            }
        }

        private static InjectionCardinality injectionCardinality(String value) {
            try {
                return InjectionCardinality.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return InjectionCardinality.UNKNOWN;
            }
        }

        private static boolean booleanValue(Map<String, AtlasValue> attributes, String name, boolean fallback) {
            AtlasValue value = attributes.get(name);
            return value instanceof AtlasValue.Bool bool ? bool.value() : fallback;
        }

        /**
         * 将 JPA 的源码映射事实归一为可被业务和运维界面直接阅读的数据目标。
         *
         * <p>实体本身可作为可靠的数据可见性边界；物理表和列仅在 {@code @Table}/{@code @Column} 中以字面量
         * 明确出现时才创建。因此读取 Atlas 的人能明确区分“已知实体”与“已知真实表/列”，不会把框架命名策略
         * 误解成源码已经证明的事实。</p>
         */
        private void addJpaPersistenceTargets() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() == FactKind.PERSISTENCE_ENTITY) {
                    String entityType = text(fact.attributes(), "entityType").orElse(fact.subject());
                    persistenceEntityFor(entityType, fact);
                }
            }
            // Fact Bundle 的稳定排序不等同于业务依赖顺序；先建立表，才能以确定方式把列放入表中。
            facts.facts().stream().filter(fact -> fact.kind() == FactKind.PERSISTENCE_TABLE).forEach(this::addPersistenceTable);
            facts.facts().stream().filter(fact -> fact.kind() == FactKind.PERSISTENCE_COLUMN).forEach(this::addPersistenceColumn);
            facts.facts().stream().filter(fact -> fact.kind() == FactKind.REPOSITORY).forEach(this::addRepositoryBinding);
        }

        private void addProjectionWrites() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.PROJECTION_WRITE) {
                    continue;
                }
                String projectionType = text(fact.attributes(), "projectionType").orElse(null);
                String entityType = text(fact.attributes(), "entityType").orElse(null);
                if (projectionType == null || entityType == null) {
                    continue;
                }
                AtlasId projectionCodeId = ensureCodeElement(projectionType, CodeElementKind.TYPE, fact.evidence());
                Projection projection = projectionFor(projectionType, projectionCodeId, fact);
                DataTarget entity = persistenceEntityFor(entityType, fact);
                addRelation(RelationKind.PROJECTS_TO, projection.id(), entity.id(), fact.evidence(), fact.attributes(),
                        "projects-to-entity:" + projection.id() + ":" + entity.id() + ":" + fact.id());

                AtlasId tableId = persistenceTableTargets.get(entityType);
                if (tableId != null) {
                    addRelation(RelationKind.WRITES_TO, projection.id(), tableId, fact.evidence(), fact.attributes(),
                            "projection-writes-to-table:" + projection.id() + ":" + tableId + ":" + fact.id());
                }
                text(fact.attributes(), "repositoryType").ifPresent(repositoryType -> {
                    AtlasId repositoryCodeId = ensureCodeElement(repositoryType, CodeElementKind.TYPE, fact.evidence());
                    addRelation(RelationKind.DEPENDS_ON, projection.id(), repositoryCodeId, fact.evidence(), fact.attributes(),
                            "projection-depends-on-repository:" + projection.id() + ":" + repositoryCodeId + ":" + fact.id());
                });
            }
        }

        private DataTarget persistenceEntityFor(String entityType, SourceFact fact) {
            AtlasId id = persistenceEntityTargets.computeIfAbsent(entityType,
                    type -> AtlasId.of("derived:data-target:persistence-entity:" + encode(type)));
            ArchitectureAsset existing = nodes.get(id);
            if (existing instanceof DataTarget dataTarget) {
                return dataTarget;
            }
            AtlasId codeId = ensureCodeElement(entityType, CodeElementKind.TYPE, fact.evidence());
            Map<String, AtlasValue> attributes = new LinkedHashMap<>();
            attributes.put("entityType", AtlasValue.text(entityType));
            text(fact.attributes(), "entityName").ifPresent(name -> attributes.put("entityName", AtlasValue.text(name)));
            attributes.put("derivedFromFact", AtlasValue.text(fact.id().value()));
            DataTarget target = new DataTarget(
                    new AssetMetadata(id, simpleName(entityType), attributes, fact.evidence()),
                    DataTargetKind.PERSISTENCE_ENTITY);
            putNode(target);
            addRelation(RelationKind.REPRESENTS, target.id(), codeId, fact.evidence(),
                    "persistence-entity-represents:" + target.id() + ":" + codeId);
            return target;
        }

        private void addPersistenceTable(SourceFact fact) {
            String entityType = text(fact.attributes(), "entityType").orElse(null);
            String tableName = text(fact.attributes(), "tableName").orElse(null);
            if (entityType == null || tableName == null) {
                return;
            }
            DataTarget entity = persistenceEntityFor(entityType, fact);
            String schema = text(fact.attributes(), "schema").orElse("");
            AtlasId tableId = AtlasId.of("derived:data-target:database-table:" + encode(entityType + ":" + schema + ":" + tableName));
            persistenceTableTargets.putIfAbsent(entityType, tableId);
            if (!nodes.containsKey(tableId)) {
                Map<String, AtlasValue> attributes = new LinkedHashMap<>();
                attributes.put("entityType", AtlasValue.text(entityType));
                attributes.put("tableName", AtlasValue.text(tableName));
                if (!schema.isBlank()) {
                    attributes.put("schema", AtlasValue.text(schema));
                }
                attributes.put("derivedFromFact", AtlasValue.text(fact.id().value()));
                putNode(new DataTarget(
                        new AssetMetadata(tableId, schema.isBlank() ? tableName : schema + "." + tableName,
                                attributes, fact.evidence()),
                        DataTargetKind.DATABASE_TABLE));
            }
            addRelation(RelationKind.WRITES_TO, entity.id(), tableId, fact.evidence(), fact.attributes(),
                    "persistence-entity-writes-to-table:" + entity.id() + ":" + tableId + ":" + fact.id());
        }

        private void addPersistenceColumn(SourceFact fact) {
            String entityType = text(fact.attributes(), "entityType").orElse(null);
            String propertyName = text(fact.attributes(), "propertyName").orElse(null);
            String columnName = text(fact.attributes(), "columnName").orElse(null);
            AtlasId tableId = entityType == null ? null : persistenceTableTargets.get(entityType);
            if (entityType == null || propertyName == null || columnName == null || tableId == null) {
                return;
            }
            AtlasId columnId = AtlasId.of("derived:data-target:database-column:" + encode(entityType + ":" + columnName));
            if (!nodes.containsKey(columnId)) {
                Map<String, AtlasValue> attributes = new LinkedHashMap<>(fact.attributes());
                attributes.put("derivedFromFact", AtlasValue.text(fact.id().value()));
                putNode(new DataTarget(
                        new AssetMetadata(columnId, columnName, attributes, fact.evidence()),
                        DataTargetKind.DATABASE_COLUMN));
            }
            addRelation(RelationKind.CONTAINS, tableId, columnId, fact.evidence(), fact.attributes(),
                    "database-table-contains-column:" + tableId + ":" + columnId + ":" + fact.id());
            AtlasId fieldCodeId = ensureCodeElement(entityType + "#" + propertyName, CodeElementKind.FIELD, fact.evidence());
            addRelation(RelationKind.REPRESENTS, nodes.get(columnId).id(), fieldCodeId, fact.evidence(),
                    "database-column-represents-field:" + columnId + ":" + fieldCodeId);
        }

        private void addRepositoryBinding(SourceFact fact) {
            String repositoryType = text(fact.attributes(), "repositoryType").orElse(null);
            String entityType = text(fact.attributes(), "entityType").orElse(null);
            if (repositoryType == null || entityType == null) {
                return;
            }
            AtlasId repositoryCodeId = ensureCodeElement(repositoryType, CodeElementKind.TYPE, fact.evidence());
            DataTarget entity = persistenceEntityFor(entityType, fact);
            addRelation(RelationKind.DEPENDS_ON, repositoryCodeId, entity.id(), fact.evidence(), fact.attributes(),
                    "repository-depends-on-persistence-entity:" + repositoryCodeId + ":" + entity.id() + ":" + fact.id());
        }

        private void addHttpEndpoints() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.HTTP_ENDPOINT) {
                    continue;
                }
                String handler = text(fact.attributes(), "handler").orElse(fact.subject());
                AtlasId handlerId = ensureCodeElement(handler, CodeElementKind.METHOD, fact.evidence());
                EndpointOperation endpoint = singleRepresenting(handlerId, EndpointOperation.class).orElse(null);
                if (endpoint == null) {
                    AtlasId endpointId = AtlasId.of("derived:endpoint:" + encode(fact.id().value()));
                    endpoint = new EndpointOperation(new AssetMetadata(
                            endpointId,
                            endpointName(fact),
                            fact.attributes(),
                            fact.evidence()),
                            "由 Spring MVC 映射注解识别的外部接口；尚未提供显式业务目的。");
                    putNode(endpoint);
                    addRelation(RelationKind.REPRESENTS, endpoint.id(), handlerId, fact.evidence(),
                            "derived-endpoint-represents:" + endpoint.id() + ":" + handlerId);
                } else {
                    replaceEndpoint(endpoint, fact.attributes(), fact.evidence());
                }
            }
        }

        private void addEventPayloadFields() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.EVENT_PAYLOAD_FIELD) {
                    continue;
                }
                String eventType = text(fact.attributes(), "eventType").orElse(null);
                String fieldName = text(fact.attributes(), "fieldName").orElse(null);
                if (eventType == null || fieldName == null) {
                    continue;
                }
                AtlasId eventCodeId = ensureCodeElement(eventType, CodeElementKind.TYPE, fact.evidence());
                DomainEvent event = eventFor(eventType, eventCodeId, fact);
                String fieldType = text(fact.attributes(), "fieldType").orElse("");
                EventFieldRole role = eventFieldRole(text(fact.attributes(), "fieldRole").orElse("UNSPECIFIED"));
                if (event.payload().stream().noneMatch(field -> field.name().equals(fieldName))) {
                    List<EventField> payload = new ArrayList<>(event.payload());
                    payload.add(new EventField(fieldName, role, fieldType.isBlank() ? "" : "Java 类型: " + fieldType));
                    nodes.put(event.id(), new DomainEvent(
                            metadataWith(event.metadata(), event.metadata().attributes(), fact.evidence()),
                            event.businessFact(),
                            payload));
                } else {
                    nodes.put(event.id(), new DomainEvent(
                            metadataWith(event.metadata(), event.metadata().attributes(), fact.evidence()),
                            event.businessFact(),
                            event.payload()));
                }
            }
        }

        private void addEventPublications() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.EVENT_PUBLICATION) {
                    continue;
                }
                String producer = text(fact.attributes(), "producer").orElse(null);
                String eventType = text(fact.attributes(), "eventType").orElse(null);
                if (producer == null || eventType == null) {
                    continue;
                }
                AtlasId producerCodeId = ensureCodeElement(producer, CodeElementKind.METHOD, fact.evidence());
                AtlasId eventCodeId = ensureCodeElement(eventType, CodeElementKind.TYPE, fact.evidence());
                DomainEvent event = eventFor(eventType, eventCodeId, fact);
                AtlasId semanticProducer = semanticOwnerForMethod(producer, producerCodeId).orElse(producerCodeId);
                addRelation(RelationKind.PRODUCES, semanticProducer, event.id(), fact.evidence(), fact.attributes(),
                        "produces:" + semanticProducer + ":" + event.id() + ":" + fact.id());
            }
        }

        private void addEventConsumptions() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.EVENT_CONSUMPTION) {
                    continue;
                }
                String eventType = text(fact.attributes(), "eventType").orElse(null);
                String consumer = text(fact.attributes(), "consumer").orElse(null);
                String consumerType = text(fact.attributes(), "consumerType").orElse(null);
                String role = text(fact.attributes(), "consumerRole").orElse("EVENT_HANDLER");
                if (eventType == null || consumer == null || consumerType == null) {
                    continue;
                }
                AtlasId eventCodeId = ensureCodeElement(eventType, CodeElementKind.TYPE, fact.evidence());
                DomainEvent event = eventFor(eventType, eventCodeId, fact);
                AtlasId consumerCodeId = ensureCodeElement(consumer, CodeElementKind.METHOD, fact.evidence());
                AtlasId consumerTypeCodeId = ensureCodeElement(consumerType, CodeElementKind.TYPE, fact.evidence());
                AtlasId target = switch (role) {
                    case "PROCESS_MANAGER" -> processManagerFor(consumerType, consumerTypeCodeId, fact).id();
                    case "PROJECTION" -> projectionFor(consumerType, consumerTypeCodeId, fact).id();
                    default -> semanticOwnerForMethod(consumer, consumerCodeId).orElse(consumerCodeId);
                };
                addRelation(RelationKind.CONSUMES, event.id(), target, fact.evidence(), fact.attributes(),
                        "consumes:" + event.id() + ":" + target + ":" + fact.id());
            }
        }

        private void addCommandHandlers() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.COMMAND_HANDLER) {
                    continue;
                }
                String handler = text(fact.attributes(), "handler").orElse(null);
                String commandType = text(fact.attributes(), "commandType").orElse(null);
                if (handler == null || commandType == null) {
                    continue;
                }
                AtlasId handlerId = ensureCodeElement(handler, CodeElementKind.METHOD, fact.evidence());
                AtlasId commandId = ensureCodeElement(commandType, CodeElementKind.TYPE, fact.evidence());
                addRelation(RelationKind.HANDLES, handlerId, commandId, fact.evidence(), fact.attributes(),
                        "handles:" + handlerId + ":" + commandId + ":" + fact.id());
            }
        }

        private void addCommandDispatches() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.COMMAND_DISPATCH) {
                    continue;
                }
                String caller = text(fact.attributes(), "caller").orElse(null);
                String commandType = text(fact.attributes(), "commandType").orElse(null);
                if (caller == null || commandType == null) {
                    continue;
                }
                AtlasId callerCodeId = ensureCodeElement(caller, CodeElementKind.METHOD, fact.evidence());
                List<ResolvedCommandType> targets = commandDispatchTargets(fact, caller, commandType);
                if (targets.isEmpty()) {
                    diagnostics.add(diagnostic("UNRESOLVED_COMMAND_DISPATCH_ARGUMENT",
                            "无法从已证明的参数值流确定命令派发类型: " + caller + " -> "
                                    + text(fact.attributes(), "commandExpression").orElse(commandType),
                            fact.evidence().getFirst().locator()));
                    continue;
                }
                for (ResolvedCommandType target : targets) {
                    String dispatchOrigin = target.originMethod().isBlank() ? caller : target.originMethod();
                    AtlasId originCodeId = dispatchOrigin.equals(caller) ? callerCodeId
                            : ensureCodeElement(dispatchOrigin, CodeElementKind.METHOD, fact.evidence());
                    AtlasId source = executionOwnerForMethod(dispatchOrigin, originCodeId);
                    AtlasId commandId = ensureCodeElement(target.type(), CodeElementKind.TYPE, fact.evidence());
                    Map<String, AtlasValue> attributes = new LinkedHashMap<>(fact.attributes());
                    attributes.put("commandResolution", AtlasValue.text(target.resolution()));
                    attributes.put("dispatchImplementationMethod", AtlasValue.text(caller));
                    attributes.put("dispatchOriginMethod", AtlasValue.text(dispatchOrigin));
                    addRelation(RelationKind.DISPATCHES, source, commandId, fact.evidence(), attributes,
                            "dispatches:" + source + ":" + commandId + ":" + target.resolution() + ":"
                                    + dispatchOrigin + ":" + fact.id());
                }
            }
        }

        /**
         * 解析由通用 Gateway helper 派发的命令。
         *
         * <p>例如 Controller 把 {@code AuthenticateMobileAppUserCommand} 传给
         * {@code sendAndRequireResult(Command, Class)}，后者再对其参数 {@code command} 调用 Gateway。源码在
         * helper 内只能看到抽象 {@code Command}，但 JVM 已证明的 {@link RelationKind#ARGUMENT_BINDS_TO} 边可以
         * 将该参数追溯至调用方的具体参数类型。只有这条值流唯一存在时才补出命令类型；复杂构造、局部重赋值或多个
         * 上游类型仍保持未决，不能任意挑一个 Handler。</p>
         */
        private List<ResolvedCommandType> commandDispatchTargets(SourceFact fact, String caller, String commandType) {
            if (!commandType.startsWith("<unresolved:")) {
                return List.of(new ResolvedCommandType(commandType, "DIRECT_SOURCE", caller));
            }
            String expression = text(fact.attributes(), "commandExpression").orElse("").trim();
            // SemanticValue 的稳定身份以 SOURCE_PARAMETER 的完整 subject（含 parameter[index]）生成，不能用
            // “方法::变量名”臆造 ID。除简单参数外，也允许已经由唯一源码返回类型证明的 fluent 实参值；复杂表达式
            // 没有这项证据时仍保持未决。
            List<SemanticValue> dispatchedParameters = nodes.values().stream()
                    .filter(SemanticValue.class::isInstance).map(SemanticValue.class::cast)
                    .filter(value -> caller.equals(text(value.attributes(), "operation").orElse("")))
                    .filter(value -> isSimpleIdentifier(expression)
                            ? value.name().equals(expression)
                            : expression.equals(text(value.attributes(), "expression").orElse("")))
                    .toList();
            if (dispatchedParameters.size() != 1) {
                return List.of();
            }
            // 一个 Command 经常穿过 Controller helper 和 Gateway helper；只回溯一跳仍会停在抽象 Command 参数，
            // 进而让“接口 → CommandHandler”的真实链路断裂。沿已证明的 ARGUMENT_BINDS_TO 反向闭包回溯，直到
            // 找到具体命令类型；visited 保证递归 helper 或值流环不会无限扩展。
            Map<AtlasId, List<AtlasId>> argumentsByParameter = new LinkedHashMap<>();
            relations.values().stream().filter(relation -> relation.kind() == RelationKind.ARGUMENT_BINDS_TO)
                    .forEach(relation -> argumentsByParameter.computeIfAbsent(relation.targetId(), ignored -> new ArrayList<>())
                            .add(relation.sourceId()));
            List<AtlasId> pending = new ArrayList<>(List.of(dispatchedParameters.getFirst().id()));
            Set<AtlasId> visited = new LinkedHashSet<>();
            Map<CommandTypeOrigin, Integer> types = new LinkedHashMap<>();
            while (!pending.isEmpty()) {
                AtlasId parameter = pending.removeFirst();
                if (!visited.add(parameter)) {
                    continue;
                }
                ArchitectureAsset current = nodes.get(parameter);
                if (current instanceof SemanticValue value) {
                    String type = normalizeCommandType(value.javaRepresentation());
                    if (isConcreteCommandType(type)) {
                        String origin = text(value.attributes(), "caller")
                                .or(() -> text(value.attributes(), "operation")).orElse("");
                        types.putIfAbsent(new CommandTypeOrigin(type, origin), visited.size());
                    }
                }
                for (AtlasId argument : argumentsByParameter.getOrDefault(parameter, List.of())) {
                    ArchitectureAsset asset = nodes.get(argument);
                    if (asset instanceof SemanticValue value) {
                        String type = normalizeCommandType(value.javaRepresentation());
                        if (isConcreteCommandType(type)) {
                            String origin = text(value.attributes(), "caller")
                                    .or(() -> text(value.attributes(), "operation")).orElse("");
                            types.putIfAbsent(new CommandTypeOrigin(type, origin), visited.size());
                        }
                    }
                    pending.add(argument);
                }
            }
            return types.keySet().stream().sorted(Comparator.comparing(CommandTypeOrigin::type)
                            .thenComparing(CommandTypeOrigin::originMethod))
                    .map(target -> new ResolvedCommandType(target.type(),
                            visited.size() > 2 ? "INTERPROCEDURAL_ARGUMENT_CHAIN" : "INTERPROCEDURAL_ARGUMENT",
                            target.originMethod())).toList();
        }

        private static boolean isSimpleIdentifier(String value) {
            return value.matches("[A-Za-z_$][A-Za-z0-9_$]*");
        }

        private static String normalizeCommandType(String value) {
            String normalized = value == null ? "" : value.trim();
            int generic = normalized.indexOf('<');
            return generic < 0 ? normalized : normalized.substring(0, generic).trim();
        }

        private static boolean isConcreteCommandType(String type) {
            if (type.isBlank() || type.equals("<inferred>")) {
                return false;
            }
            String simple = simpleName(type);
            return !simple.equals("Command") && !simple.equals("CommandMessage") && !simple.equals("Object");
        }

        private record CommandTypeOrigin(String type, String originMethod) {
        }

        /** 由通用 Gateway helper 产生的派发归属于把具体 Command 传入该 helper 的调用点。 */
        private record ResolvedCommandType(String type, String resolution, String originMethod) {
        }

        private void addProcessLifecycle() {
            for (SourceFact fact : facts.facts()) {
                if (!List.of(FactKind.PROCESS_START, FactKind.PROCESS_WAIT, FactKind.PROCESS_COMPLETION).contains(fact.kind())) {
                    continue;
                }
                String managerType = text(fact.attributes(), "processManager").orElse(null);
                String eventType = text(fact.attributes(), "eventType").orElse(null);
                if (managerType == null || eventType == null) {
                    continue;
                }
                AtlasId managerCodeId = ensureCodeElement(managerType, CodeElementKind.TYPE, fact.evidence());
                ProcessManager manager = processManagerFor(managerType, managerCodeId, fact);
                AtlasId eventCodeId = ensureCodeElement(eventType, CodeElementKind.TYPE, fact.evidence());
                DomainEvent event = eventFor(eventType, eventCodeId, fact);
                RelationKind relationKind = switch (fact.kind()) {
                    case PROCESS_START -> RelationKind.STARTS;
                    case PROCESS_WAIT -> RelationKind.WAITS_FOR;
                    case PROCESS_COMPLETION -> RelationKind.COMPLETES;
                    default -> throw new IllegalStateException("不支持的流程事实: " + fact.kind());
                };
                if (relationKind == RelationKind.STARTS) {
                    addRelation(relationKind, event.id(), manager.id(), fact.evidence(), fact.attributes(),
                            "starts:" + event.id() + ":" + manager.id() + ":" + fact.id());
                } else {
                    addRelation(relationKind, manager.id(), event.id(), fact.evidence(), fact.attributes(),
                            relationKind.name().toLowerCase() + ":" + manager.id() + ":" + event.id() + ":" + fact.id());
                }
            }
        }

        private void addProcessDispatches() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.PROCESS_DISPATCH) {
                    continue;
                }
                String managerType = text(fact.attributes(), "processManager").orElse(null);
                String commandType = text(fact.attributes(), "commandType").orElse(null);
                if (managerType == null || commandType == null) {
                    continue;
                }
                AtlasId managerCodeId = ensureCodeElement(managerType, CodeElementKind.TYPE, fact.evidence());
                ProcessManager manager = processManagerFor(managerType, managerCodeId, fact);
                AtlasId commandId = ensureCodeElement(commandType, CodeElementKind.TYPE, fact.evidence());
                addRelation(RelationKind.DISPATCHES, manager.id(), commandId, fact.evidence(), fact.attributes(),
                        "process-dispatches:" + manager.id() + ":" + commandId + ":" + fact.id());
            }
        }

        private AtlasId ensureCodeElement(String sourceKey, CodeElementKind kind, List<Evidence> evidence) {
            AtlasId id = codeId(sourceKey);
            if (!nodes.containsKey(id)) {
                upsertCodeElement(sourceKey, kind, evidence, evidence.getFirst().locator());
            }
            return id;
        }

        private DomainEvent eventFor(String eventType, AtlasId eventCodeId, SourceFact fact) {
            Optional<DomainEvent> explicit = singleRepresenting(eventCodeId, DomainEvent.class);
            if (explicit.isPresent()) {
                return explicit.get();
            }
            AtlasId id = AtlasId.of("derived:event:" + encode(eventType));
            ArchitectureAsset existing = nodes.get(id);
            if (existing instanceof DomainEvent event) {
                return event;
            }
            DomainEvent event = new DomainEvent(
                    new AssetMetadata(id, simpleName(eventType), Map.of(
                            "derivedFromFact", AtlasValue.text(fact.id().value()),
                            "eventType", AtlasValue.text(eventType)), fact.evidence()),
                    "由 Axon 源码识别的领域事件；尚未提供显式业务事实说明。",
                    List.of());
            putNode(event);
            addRelation(RelationKind.REPRESENTS, event.id(), eventCodeId, fact.evidence(),
                    "derived-event-represents:" + event.id() + ":" + eventCodeId);
            return event;
        }

        private ProcessManager processManagerFor(String typeName, AtlasId typeCodeId, SourceFact fact) {
            Optional<ProcessManager> explicit = singleRepresenting(typeCodeId, ProcessManager.class);
            if (explicit.isPresent()) {
                return explicit.get();
            }
            AtlasId id = AtlasId.of("derived:process-manager:" + encode(typeName));
            ArchitectureAsset existing = nodes.get(id);
            if (existing instanceof ProcessManager processManager) {
                return processManager;
            }
            ProcessManager processManager = new ProcessManager(
                    new AssetMetadata(id, simpleName(typeName), Map.of(
                            "derivedFromFact", AtlasValue.text(fact.id().value()),
                            "implementationType", AtlasValue.text(typeName)), fact.evidence()),
                    "由 Axon Saga 源码识别的跨聚合流程；尚未提供显式业务职责说明。",
                    "");
            putNode(processManager);
            addRelation(RelationKind.REPRESENTS, processManager.id(), typeCodeId, fact.evidence(),
                    "derived-process-represents:" + processManager.id() + ":" + typeCodeId);
            return processManager;
        }

        private Projection projectionFor(String typeName, AtlasId typeCodeId, SourceFact fact) {
            Optional<Projection> explicit = singleRepresenting(typeCodeId, Projection.class);
            if (explicit.isPresent()) {
                return explicit.get();
            }
            AtlasId id = AtlasId.of("derived:projection:" + encode(typeName));
            ArchitectureAsset existing = nodes.get(id);
            if (existing instanceof Projection projection) {
                return projection;
            }
            Projection projection = new Projection(
                    new AssetMetadata(id, simpleName(typeName), Map.of(
                            "derivedFromFact", AtlasValue.text(fact.id().value()),
                            "implementationType", AtlasValue.text(typeName)), fact.evidence()),
                    "由 Axon 事件处理器识别的读模型投影；尚未提供显式业务用途说明。");
            putNode(projection);
            addRelation(RelationKind.REPRESENTS, projection.id(), typeCodeId, fact.evidence(),
                    "derived-projection-represents:" + projection.id() + ":" + typeCodeId);
            return projection;
        }

        private Optional<AtlasId> semanticOwnerForMethod(String method, AtlasId methodCodeId) {
            String owner = method.contains("#") ? method.substring(0, method.indexOf('#')) : "";
            if (owner.isBlank()) {
                return Optional.empty();
            }
            AtlasId ownerCodeId = codeId(owner);
            List<ArchitectureAsset> owners = semanticRepresentations(ownerCodeId).stream()
                    .filter(asset -> asset instanceof BusinessConcept || asset instanceof ApplicationUseCase)
                    .toList();
            return owners.size() == 1 ? Optional.of(owners.getFirst().id()) : Optional.empty();
        }

        private AtlasId executionOwnerForMethod(String method, AtlasId methodCodeId) {
            return singleRepresenting(methodCodeId, EndpointOperation.class).map(ArchitectureAsset::id)
                    .orElseGet(() -> semanticOwnerForMethod(method, methodCodeId).orElse(methodCodeId));
        }

        private <T extends ArchitectureAsset> Optional<T> singleRepresenting(AtlasId codeId, Class<T> assetType) {
            List<T> candidates = semanticRepresentations(codeId).stream()
                    .filter(assetType::isInstance)
                    .map(assetType::cast)
                    .toList();
            return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
        }

        private List<ArchitectureAsset> semanticRepresentations(AtlasId codeId) {
            return relations.values().stream()
                    .filter(relation -> relation.kind() == RelationKind.REPRESENTS && relation.targetId().equals(codeId))
                    .map(AtlasRelation::sourceId)
                    .map(nodes::get)
                    .filter(Objects::nonNull)
                    .filter(asset -> !(asset instanceof CodeElement))
                    .toList();
        }

        private void replaceEndpoint(EndpointOperation endpoint, Map<String, AtlasValue> discoveredAttributes, List<Evidence> evidence) {
            Map<String, AtlasValue> attributes = new LinkedHashMap<>(endpoint.attributes());
            discoveredAttributes.forEach(attributes::putIfAbsent);
            nodes.put(endpoint.id(), new EndpointOperation(
                    metadataWith(endpoint.metadata(), attributes, evidence),
                    endpoint.purpose()));
        }

        private AssetMetadata metadataWith(AssetMetadata original, Map<String, AtlasValue> attributes, List<Evidence> evidence) {
            return new AssetMetadata(original.id(), original.name(), attributes, mergeEvidence(original.evidence(), evidence));
        }

        private static String endpointName(SourceFact fact) {
            String paths = values(fact.attributes(), "paths").stream().findFirst().orElse("/");
            String methods = values(fact.attributes(), "httpMethods").stream().findFirst().orElse("HTTP");
            return methods + " " + paths;
        }

        private static String simpleName(String qualifiedName) {
            int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
            return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
        }

        private static EventFieldRole eventFieldRole(String value) {
            try {
                return EventFieldRole.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return EventFieldRole.UNSPECIFIED;
            }
        }

        private void addIntentAssets(List<IntentDeclaration> declarations) {
            for (IntentDeclaration declaration : declarations) {
                AtlasId assetId = assetIdOf(declaration);
                String codeKey = sourceKey(declaration.target());
                AtlasId codeId = resolvedCodeId(declaration.target(), codeKey);
                List<Evidence> evidence = mergeEvidence(
                        List.of(declaration.evidence()),
                        sourceEvidenceFor(declaration.target()));
                upsertCodeElement(codeKey, codeKind(declaration.target()), evidence, evidence.getFirst().locator());
                if (applyOperationSupplement(declaration, codeId, evidence)) {
                    continue;
                }
                ArchitectureAsset asset = createIntentAsset(declaration, assetId, codeId, evidence);
                if (!putNode(asset)) {
                    diagnostics.add(diagnostic("DUPLICATE_ASSET_ID",
                            "多个显式意图声明了同一个 Atlas 资产身份: " + assetId, declaration.evidence().locator()));
                    continue;
                }
                addRelation(RelationKind.REPRESENTS, asset.id(), codeId, evidence, "represents:" + asset.id() + ":" + codeId);
                if (asset instanceof DomainConstraint) {
                    addRelation(RelationKind.CONSTRAINS, asset.id(), codeId, evidence, "constrains:" + asset.id() + ":" + codeId);
                }
                linkDeclaredAssetToInferredSemantics(asset, codeId, declaration, evidence);
            }
        }

        /**
         * `@AtlasPrecondition` 等注解是既有操作的补充契约，而不是另一个没有输入/调用边的虚假操作。
         * 若源码操作不可得，仍按一般 Intent 保留为独立声明，以免沉默丢失信息。
         */
        private boolean applyOperationSupplement(IntentDeclaration declaration, AtlasId codeId, List<Evidence> evidence) {
            String annotation = simpleName(declaration.annotationType());
            if (!Set.of("AtlasPrecondition", "AtlasPostcondition", "AtlasEffect", "AtlasFailure").contains(annotation)) {
                return false;
            }
            List<OperationContract> operations = operationsForCode(codeId);
            if (operations.size() != 1) {
                return false;
            }
            OperationContract current = operations.getFirst();
            Map<String, AtlasValue> attributes = new LinkedHashMap<>(current.attributes());
            declaration.attributes().forEach((key, value) -> attributes.put("declared." + annotation + "." + key, value));
            List<OperationOutcome> outcomes = new ArrayList<>(current.outcomes());
            List<OperationFailure> failures = new ArrayList<>(current.failures());
            if (annotation.equals("AtlasEffect")) {
                outcomes.add(new OperationOutcome(
                        operationOutcomeKind(text(declaration.attributes(), "kind").orElse("CUSTOM")),
                        firstNonBlank(text(declaration.attributes(), "target"), Optional.empty(), "declared-effect"),
                        "<declared>",
                        text(declaration.attributes(), "condition").orElse(""),
                        text(declaration.attributes(), "description").orElse("由 Atlas 注解声明的副作用")));
            }
            if (annotation.equals("AtlasFailure")) {
                failures.add(new OperationFailure(
                        text(declaration.attributes(), "exception").orElse("java.lang.RuntimeException"),
                        text(declaration.attributes(), "condition").orElse(""),
                        firstNonBlank(text(declaration.attributes(), "description"), Optional.empty(), "由 Atlas 注解声明的失败路径")));
            }
            OperationContract enriched = new OperationContract(
                    new AssetMetadata(current.id(), current.name(), attributes, mergeEvidence(current.evidence(), evidence)),
                    current.operationKind(), current.javaSignature(), current.locator(), current.inputs(), outcomes, failures);
            nodes.put(enriched.id(), enriched);
            return true;
        }

        private void linkDeclaredAssetToInferredSemantics(
                ArchitectureAsset asset,
                AtlasId codeId,
                IntentDeclaration declaration,
                List<Evidence> evidence) {
            if (asset instanceof OperationContract) {
                operationsForCode(codeId).stream().filter(operation -> !operation.id().equals(asset.id())).forEach(operation ->
                        addRelation(RelationKind.IMPLEMENTS, operation.id(), asset.id(), evidence,
                                "inferred-operation-implements-declared-operation:" + operation.id() + ":" + asset.id()));
            }
            if (!(asset instanceof SemanticValue)) {
                return;
            }
            List<SemanticValue> inferred = semanticValuesForCode(codeId);
            for (SemanticValue value : inferred.stream().filter(value -> !value.id().equals(asset.id())).toList()) {
                addRelation(RelationKind.DERIVES_FROM, value.id(), asset.id(), evidence,
                        "inferred-value-derives-from-declared-value:" + value.id() + ":" + asset.id());
            }
            if (declaration.target().kind().equals("METHOD") || declaration.target().kind().equals("CONSTRUCTOR")) {
                for (OperationContract operation : operationsForCode(codeId)) {
                    List<AtlasRelation> returns = relations.values().stream()
                            .filter(relation -> relation.kind() == RelationKind.RETURNS && relation.sourceId().equals(operation.id())).toList();
                    returns.forEach(relation -> addRelation(RelationKind.DERIVES_FROM, relation.targetId(), asset.id(), evidence,
                                    "return-value-derives-from-declared-output:" + relation.targetId() + ":" + asset.id()));
                }
            }
        }

        private List<OperationContract> operationsForCode(AtlasId codeId) {
            return relations.values().stream().filter(relation -> relation.kind() == RelationKind.REPRESENTS && relation.targetId().equals(codeId))
                    .map(AtlasRelation::sourceId).map(nodes::get).filter(OperationContract.class::isInstance).map(OperationContract.class::cast)
                    .sorted(Comparator.comparing(OperationContract::id)).toList();
        }

        private List<SemanticValue> semanticValuesForCode(AtlasId codeId) {
            return relations.values().stream().filter(relation -> relation.kind() == RelationKind.REPRESENTS && relation.targetId().equals(codeId))
                    .map(AtlasRelation::sourceId).map(nodes::get).filter(SemanticValue.class::isInstance).map(SemanticValue.class::cast)
                    .sorted(Comparator.comparing(SemanticValue::id)).toList();
        }

        private static OperationOutcomeKind operationOutcomeKind(String value) {
            try {
                return OperationOutcomeKind.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return OperationOutcomeKind.CUSTOM;
            }
        }

        private void addDerivedValidationConstraints() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.VALIDATION_RULE) {
                    continue;
                }
                String owner = text(fact.attributes(), "owner").orElse(fact.subject());
                AtlasId subjectId = codeId(owner);
                if (!nodes.containsKey(subjectId)) {
                    upsertCodeElement(owner, CodeElementKind.METHOD, fact.evidence(), fact.evidence().getFirst().locator());
                }
                boolean alreadyDescribed = nodes.values().stream()
                        .filter(DomainConstraint.class::isInstance)
                        .map(DomainConstraint.class::cast)
                        .anyMatch(constraint -> constraint.subjectId().equals(subjectId));
                if (alreadyDescribed) {
                    continue;
                }
                String expression = text(fact.attributes(), "expression").orElse("未提取表达式");
                String enforcingCall = text(fact.attributes(), "enforcingCall").orElse("未识别校验调用");
                String failureMessage = text(fact.attributes(), "failureMessage").orElse("");
                String rejection = failureMessage.isBlank() ? "" : "；拒绝原因/业务消息：" + failureMessage;
                DomainConstraint constraint = new DomainConstraint(
                        new AssetMetadata(
                                AtlasId.of("derived:constraint:" + encode(fact.id().value())),
                                "代码校验规则",
                                Map.of("derivedFromFact", AtlasValue.text(fact.id().value())),
                                fact.evidence()),
                        subjectId,
                        constraintKindFor(fact),
                        EnforcementLevel.HARD,
                        "在 " + owner + " 中通过 " + enforcingCall + " 强制校验：" + expression + rejection);
                putNode(constraint);
                addRelation(RelationKind.CONSTRAINS, constraint.id(), subjectId, fact.evidence(),
                        "derived-constrains:" + constraint.id() + ":" + subjectId);
            }
        }

        private void addResolvedCalls() {
            List<CodeElement> codeElements = nodes.values().stream()
                    .filter(CodeElement.class::isInstance)
                    .map(CodeElement.class::cast)
                    .toList();
            Map<String, List<CodeElement>> methodsByName = new LinkedHashMap<>();
            Map<String, List<CodeElement>> methodsByExactOwnerAndName = new LinkedHashMap<>();
            Map<String, List<CodeElement>> methodsBySimpleOwnerAndName = new LinkedHashMap<>();
            for (CodeElement codeElement : codeElements) {
                if (codeElement.elementKind() != CodeElementKind.METHOD) {
                    continue;
                }
                String qualifiedMethod = codeElement.qualifiedName();
                String methodName = methodName(qualifiedMethod);
                String owner = methodOwner(qualifiedMethod);
                if (methodName.isBlank() || owner.isBlank()) {
                    continue;
                }
                methodsByName.computeIfAbsent(methodName, ignored -> new ArrayList<>()).add(codeElement);
                methodsByExactOwnerAndName.computeIfAbsent(ownerAndMethodKey(owner, methodName), ignored -> new ArrayList<>())
                        .add(codeElement);
                methodsBySimpleOwnerAndName.computeIfAbsent(ownerAndMethodKey(simpleName(owner), methodName), ignored -> new ArrayList<>())
                        .add(codeElement);
            }
            Map<String, Map<String, List<String>>> injectionTypesByOwnerAndMember = injectionTypesByOwnerAndMember();
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.METHOD_CALL) {
                    continue;
                }
                Optional<String> caller = text(fact.attributes(), "caller");
                Optional<String> ownerHint = text(fact.attributes(), "calleeOwnerHint");
                Optional<String> methodName = text(fact.attributes(), "calleeMethodName");
                if (caller.isEmpty() || methodName.isEmpty()) {
                    continue;
                }
                AtlasId sourceId = codeId(caller.get());
                if (!nodes.containsKey(sourceId)) {
                    continue;
                }
                Optional<String> injectedReceiverType = injectionRequestedType(injectionTypesByOwnerAndMember, caller.get(),
                        text(fact.attributes(), "calleeExpression").orElse(""));
                List<CodeElement> methodCandidates = methodsByName.getOrDefault(methodName.get(), List.of());
                List<CodeElement> candidates = injectedReceiverType
                        .map(type -> methodsForOwner(methodsByExactOwnerAndName, methodsBySimpleOwnerAndName, methodName.get(), type))
                        .orElseGet(() -> ownerHint.filter(ResolutionState::looksLikeTypeHint)
                                .map(type -> methodsForOwner(
                                        methodsByExactOwnerAndName, methodsBySimpleOwnerAndName, methodName.get(), type))
                                .orElse(methodCandidates));
                if (candidates.size() == 1) {
                    CodeElement target = candidates.getFirst();
                    Map<String, AtlasValue> attributes = new LinkedHashMap<>(fact.attributes());
                    attributes.put("resolutionStatus", AtlasValue.text("STATIC_RESOLVED"));
                    injectedReceiverType.ifPresent(type -> attributes.put("receiverDeclaredType", AtlasValue.text(type)));
                    addRelation(RelationKind.CALLS, sourceId, target.id(), fact.evidence(), Map.copyOf(attributes),
                            "calls:" + sourceId + ":" + target.id() + ":" + fact.id());
                } else if (candidates.size() > 1) {
                    diagnostics.add(diagnostic("AMBIGUOUS_STATIC_CALL",
                            "无法唯一解析静态调用目标: " + fact.subject(), fact.evidence().getFirst().locator()));
                }
            }
        }

        /**
         * JVM AST 对 {@code gateway.charge()} 只能给出变量名 gateway；如果 Spring 分析器已经证明同一组件存在
         * 名为 gateway 的注入点，就以其声明类型定位接口/父类方法。局部变量或无法唯一匹配的名字不会走这条路径。
         */
        private Map<String, Map<String, List<String>>> injectionTypesByOwnerAndMember() {
            Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.BEAN_INJECTION) {
                    continue;
                }
                String ownerType = text(fact.attributes(), "ownerType").orElse("");
                String memberName = text(fact.attributes(), "memberName").orElse("");
                String requestedType = text(fact.attributes(), "requestedType").orElse("");
                if (ownerType.isBlank() || memberName.isBlank() || requestedType.isBlank()) {
                    continue;
                }
                result.computeIfAbsent(ownerType, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(memberName, ignored -> new ArrayList<>()).add(requestedType);
            }
            result.values().forEach(members -> members.replaceAll((ignored, types) -> types.stream().distinct().toList()));
            return Map.copyOf(result);
        }

        private static Optional<String> injectionRequestedType(
                Map<String, Map<String, List<String>>> injectionTypesByOwnerAndMember,
                String caller,
                String calleeExpression) {
            String receiver = receiverName(calleeExpression);
            if (receiver.isBlank()) {
                return Optional.empty();
            }
            int separator = caller.lastIndexOf('#');
            if (separator < 1) {
                return Optional.empty();
            }
            String ownerType = caller.substring(0, separator);
            List<String> types = injectionTypesByOwnerAndMember.getOrDefault(ownerType, Map.of())
                    .getOrDefault(receiver, List.of());
            return types.size() == 1 ? Optional.of(types.getFirst()) : Optional.empty();
        }

        private static String receiverName(String expression) {
            String normalized = expression == null ? "" : expression.trim();
            int dot = normalized.lastIndexOf('.');
            if (dot <= 0) {
                return "";
            }
            String receiver = normalized.substring(0, dot).trim();
            if (receiver.startsWith("this.")) {
                receiver = receiver.substring("this.".length());
            }
            return receiver.contains(".") || receiver.contains("(") || receiver.contains("[") ? "" : receiver;
        }

        private static boolean looksLikeTypeHint(String value) {
            String simple = simpleName(value == null ? "" : value.trim());
            return !simple.isEmpty() && Character.isUpperCase(simple.charAt(0));
        }

        private static List<CodeElement> methodsForOwner(
                Map<String, List<CodeElement>> methodsByExactOwnerAndName,
                Map<String, List<CodeElement>> methodsBySimpleOwnerAndName,
                String methodName,
                String type) {
            String expected = type == null ? "" : type.trim().replace(".class", "");
            int generic = expected.indexOf('<');
            if (generic >= 0) {
                expected = expected.substring(0, generic);
            }
            return expected.contains(".")
                    ? methodsByExactOwnerAndName.getOrDefault(ownerAndMethodKey(expected, methodName), List.of())
                    : methodsBySimpleOwnerAndName.getOrDefault(ownerAndMethodKey(simpleName(expected), methodName), List.of());
        }

        private static String ownerAndMethodKey(String owner, String methodName) {
            return owner + "\u0000" + methodName;
        }

        private static String methodOwner(String qualifiedMethod) {
            int separator = qualifiedMethod.lastIndexOf('#');
            return separator < 1 ? "" : qualifiedMethod.substring(0, separator);
        }

        private static int methodParameterCount(String qualifiedMethod) {
            int open = qualifiedMethod.indexOf('(');
            int close = qualifiedMethod.lastIndexOf(')');
            if (open < 0 || close < open) {
                return -1;
            }
            String parameters = qualifiedMethod.substring(open + 1, close).trim();
            if (parameters.isEmpty()) {
                return 0;
            }
            int count = 1;
            int genericDepth = 0;
            for (int index = 0; index < parameters.length(); index++) {
                char character = parameters.charAt(index);
                if (character == '<') {
                    genericDepth++;
                } else if (character == '>') {
                    genericDepth = Math.max(0, genericDepth - 1);
                } else if (character == ',' && genericDepth == 0) {
                    count++;
                }
            }
            return count;
        }

        private ArchitectureAsset createIntentAsset(
                IntentDeclaration declaration,
                AtlasId assetId,
                AtlasId codeId,
                List<Evidence> evidence) {
            Map<String, AtlasValue> attributes = declaration.attributes();
            AssetMetadata metadata = new AssetMetadata(
                    assetId,
                    firstNonBlank(text(attributes, "name"), text(attributes, "purpose"),
                            simpleName(declaration.annotationType())),
                    attributes,
                    evidence);
            return switch (declaration.assetKind()) {
                case BUSINESS_CONCEPT -> new BusinessConcept(metadata,
                        firstNonBlank(text(attributes, "responsibility"), text(attributes, "description"),
                                "未提供职责说明"));
                case DOMAIN_CONSTRAINT -> new DomainConstraint(
                        metadata,
                        codeId,
                        constraintKind(firstNonBlank(text(attributes, "kind"), Optional.empty(), "CUSTOM")),
                        EnforcementLevel.HARD,
                        firstNonBlank(text(attributes, "businessMeaning"), text(attributes, "expression"), "未提供规则说明"));
                case DOMAIN_EVENT -> new DomainEvent(metadata,
                        firstNonBlank(text(attributes, "fact"), Optional.empty(), "未提供业务事实说明"),
                        values(attributes, "payload").stream()
                                .map(value -> new EventField(value, EventFieldRole.UNSPECIFIED, ""))
                                .toList());
                case APPLICATION_USE_CASE -> new ApplicationUseCase(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供用例目的"));
                case PROJECTION -> new Projection(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供投影目的"));
                case PROCESS_MANAGER -> new ProcessManager(metadata,
                        firstNonBlank(text(attributes, "responsibility"), Optional.empty(), "未提供流程职责"),
                        text(attributes, "completionCondition").orElse(""));
                case ENDPOINT_OPERATION -> new EndpointOperation(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供接口目的"));
                case DATA_TARGET -> new DataTarget(metadata, DataTargetKind.UNSPECIFIED);
                case EXTERNAL_BOUNDARY -> new ExternalBoundary(metadata, BoundaryKind.UNSPECIFIED,
                        firstNonBlank(text(attributes, "responsibility"), Optional.empty(), "未提供边界职责"));
                case CODE_ELEMENT -> new CodeElement(metadata, codeKind(declaration.target()), sourceKey(declaration.target()),
                        declaration.evidence().locator());
                case SEMANTIC_VALUE -> new SemanticValue(metadata,
                        semanticValueCategory(firstNonBlank(text(attributes, "category"), Optional.empty(), "CUSTOM")),
                        firstNonBlank(text(attributes, "javaRepresentation"), Optional.empty(), "java.lang.Object"),
                        ValueCardinality.SINGLE,
                        ValueNullability.UNKNOWN,
                        TrustLevel.UNKNOWN,
                        DataSensitivity.UNKNOWN,
                        firstNonBlank(text(attributes, "lifecycleState"), Optional.empty(), "DECLARED"));
                case OPERATION_CONTRACT -> new OperationContract(metadata, OperationKind.CUSTOM, sourceKey(declaration.target()),
                        declaration.evidence().locator(), List.of(), List.of(), List.of());
                case COMPONENT_DEFINITION -> new ComponentDefinition(metadata, ComponentKind.CUSTOM,
                        firstNonBlank(text(attributes, "beanName"), Optional.empty(), simpleName(sourceKey(declaration.target()))),
                        declaration.target().binaryName(), declaration.target().binaryName(), ComponentScope.UNKNOWN, false,
                        List.of(), ConditionExpression.always());
                case INJECTION_POINT -> new InjectionPoint(metadata, codeId(sourceKey(declaration.target())),
                        firstNonBlank(text(attributes, "requestedType"), Optional.empty(), "java.lang.Object"),
                        InjectionKind.CUSTOM, InjectionCardinality.UNKNOWN, true, List.of());
                case CONFIGURATION_PROPERTY -> new ConfigurationProperty(metadata,
                        firstNonBlank(text(attributes, "propertyKey"), Optional.empty(), metadata.name()),
                        text(attributes, "declaredDefault").orElse(""), DataSensitivity.UNKNOWN);
                case CONFIGURATION_SCENARIO -> new ConfigurationScenario(metadata, List.of(), Map.of(), List.of());
            };
        }

        private static SemanticValueCategory semanticValueCategory(String value) {
            try {
                return SemanticValueCategory.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return SemanticValueCategory.CUSTOM;
            }
        }

        private AtlasId resolvedCodeId(IntentTarget target, String defaultKey) {
            String exactKey = sourceKey(target);
            if (nodes.containsKey(codeId(exactKey))) {
                return codeId(exactKey);
            }
            if (!target.memberDescriptor().isBlank()) {
                String methodName = target.memberDescriptor().substring(0, target.memberDescriptor().indexOf('('));
                List<AtlasId> sameMethod = nodes.values().stream()
                        .filter(CodeElement.class::isInstance)
                        .map(CodeElement.class::cast)
                        .filter(element -> element.elementKind() == CodeElementKind.METHOD)
                        .filter(element -> element.qualifiedName().startsWith(target.binaryName() + "#" + methodName + "("))
                        .map(CodeElement::id)
                        .toList();
                if (sameMethod.size() == 1) {
                    return sameMethod.getFirst();
                }
            }
            return codeId(defaultKey);
        }

        private List<Evidence> sourceEvidenceFor(IntentTarget target) {
            String key = sourceKey(target);
            List<SourceFact> exact = factsBySubject.getOrDefault(key, List.of());
            if (!exact.isEmpty()) {
                return exact.stream().flatMap(fact -> fact.evidence().stream()).toList();
            }
            if (!target.memberDescriptor().isBlank()) {
                String methodName = target.memberDescriptor().substring(0, target.memberDescriptor().indexOf('('));
                return facts.facts().stream()
                        .filter(fact -> fact.kind() == FactKind.SOURCE_METHOD)
                        .filter(fact -> fact.subject().startsWith(target.binaryName() + "#" + methodName + "("))
                        .flatMap(fact -> fact.evidence().stream())
                        .toList();
            }
            return List.of();
        }

        private void upsertCodeElement(String sourceKey, CodeElementKind kind, List<Evidence> evidence, SourceLocator locator) {
            AtlasId id = codeId(sourceKey);
            ArchitectureAsset existing = nodes.get(id);
            if (existing == null) {
                nodes.put(id, new CodeElement(
                        new AssetMetadata(id, sourceKey, Map.of(), evidence),
                        kind,
                        sourceKey,
                        locator));
                return;
            }
            if (existing instanceof CodeElement codeElement) {
                nodes.put(id, new CodeElement(
                        new AssetMetadata(id, codeElement.name(), codeElement.attributes(), mergeEvidence(codeElement.evidence(), evidence)),
                        codeElement.elementKind(),
                        codeElement.qualifiedName(),
                        codeElement.locator()));
            }
        }

        private boolean putNode(ArchitectureAsset node) {
            return nodes.putIfAbsent(node.id(), node) == null;
        }

        private void addRelation(RelationKind kind, AtlasId source, AtlasId target, List<Evidence> evidence, String identity) {
            addRelation(kind, source, target, evidence, Map.of(), identity);
        }

        private void addRelation(
                RelationKind kind,
                AtlasId source,
                AtlasId target,
                List<Evidence> evidence,
                Map<String, AtlasValue> attributes,
                String identity) {
            // AtlasGraph 不允许自环。真实源码中的递归调用、重复映射或不完整的框架事实都可能在归一化后
            // 指向同一个资产；它们不应使整个 Package 无法生成，更不能被伪造为一条不同的关系。
            if (source.equals(target)) {
                diagnostics.add(diagnostic("SELF_REFERENTIAL_RELATION_OMITTED",
                        "已省略无法表示为 Atlas 图边的自引用 " + kind + ": " + identity,
                        evidence.getFirst().locator()));
                return;
            }
            AtlasId id = AtlasId.of("relation:" + encode(identity));
            relations.putIfAbsent(id, new AtlasRelation(id, kind, source, target, attributes, evidence));
        }

        private static AtlasId assetIdOf(IntentDeclaration declaration) {
            Optional<String> explicitId = text(declaration.attributes(), "id");
            if (explicitId.isPresent() && !explicitId.get().chars().anyMatch(Character::isWhitespace)) {
                return AtlasId.of(explicitId.get());
            }
            return AtlasId.of("intent-asset:" + encode(declaration.id().value()));
        }

        /** 从统一的 {@code Type#method(parameterTypes)} 源码事实身份中取得方法名。 */
        private static String methodName(String methodSubject) {
            int ownerSeparator = methodSubject.lastIndexOf('#');
            int parameterStart = methodSubject.indexOf('(', Math.max(0, ownerSeparator));
            if (ownerSeparator < 0 || parameterStart <= ownerSeparator + 1) {
                return "";
            }
            return methodSubject.substring(ownerSeparator + 1, parameterStart);
        }

        private static AtlasId codeId(String sourceKey) {
            return AtlasId.of("code:" + encode(sourceKey));
        }

        private static AtlasId operationId(String methodSubject) {
            return AtlasId.of("derived:operation:" + encode(methodSubject));
        }

        private static AtlasId semanticValueId(String identity) {
            return AtlasId.of("derived:semantic-value:" + encode(identity));
        }

        private static AtlasId componentId(String beanName, String candidateType, AtlasId factId) {
            return AtlasId.of("derived:component:" + encode(beanName + ":" + candidateType + ":" + factId.value()));
        }

        private static String sourceKey(IntentTarget target) {
            return target.memberDescriptor().isBlank()
                    ? target.binaryName()
                    : target.binaryName() + "#" + target.memberDescriptor();
        }

        private static CodeElementKind codeKind(IntentTarget target) {
            return switch (target.kind()) {
                case "METHOD" -> CodeElementKind.METHOD;
                case "CONSTRUCTOR" -> CodeElementKind.CONSTRUCTOR;
                case "FIELD", "ENUM_CONSTANT" -> CodeElementKind.FIELD;
                case "RECORD_COMPONENT" -> CodeElementKind.RECORD_COMPONENT;
                case "PARAMETER" -> CodeElementKind.PARAMETER;
                case "PACKAGE" -> CodeElementKind.PACKAGE;
                case "ANNOTATION_TYPE" -> CodeElementKind.ANNOTATION;
                default -> CodeElementKind.TYPE;
            };
        }

        private static DomainConstraintKind constraintKind(String annotationKind) {
            return switch (annotationKind) {
                case "NUMERIC_RANGE" -> DomainConstraintKind.VALUE_RANGE;
                default -> {
                    try {
                        yield DomainConstraintKind.valueOf(annotationKind);
                    } catch (IllegalArgumentException ignored) {
                        yield DomainConstraintKind.CUSTOM;
                    }
                }
            };
        }

        private static DomainConstraintKind constraintKindFor(SourceFact fact) {
            return text(fact.attributes(), "ruleType").filter("PATTERN"::equals)
                    .map(ignored -> DomainConstraintKind.IDENTITY_FORMAT)
                    .orElse(DomainConstraintKind.EVENT_PRECONDITION);
        }

        private static Optional<String> text(Map<String, AtlasValue> attributes, String name) {
            AtlasValue value = attributes.get(name);
            return value instanceof AtlasValue.Text text && !text.value().isBlank()
                    ? Optional.of(text.value().trim())
                    : Optional.empty();
        }

        private static int integer(Map<String, AtlasValue> attributes, String name, int fallback) {
            AtlasValue value = attributes.get(name);
            if (value instanceof AtlasValue.Decimal decimal) {
                try {
                    return decimal.value().intValueExact();
                } catch (ArithmeticException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        private static List<String> values(Map<String, AtlasValue> attributes, String name) {
            AtlasValue value = attributes.get(name);
            if (value instanceof AtlasValue.Values values) {
                return values.values().stream()
                        .filter(AtlasValue.Text.class::isInstance)
                        .map(AtlasValue.Text.class::cast)
                        .map(AtlasValue.Text::value)
                        .filter(entry -> !entry.isBlank())
                        .toList();
            }
            return text(attributes, name).map(List::of).orElse(List.of());
        }

        private static String requiredText(Map<String, AtlasValue> attributes, String name, String fallback) {
            return text(attributes, name).orElse(fallback);
        }

        private static String firstNonBlank(Optional<String> first, Optional<String> second, String fallback) {
            return first.or(() -> second).orElse(fallback);
        }

        private static List<Evidence> mergeEvidence(List<Evidence> first, List<Evidence> second) {
            Map<AtlasId, Evidence> byId = new LinkedHashMap<>();
            Stream.concat(first.stream(), second.stream()).forEach(evidence -> byId.putIfAbsent(evidence.id(), evidence));
            return byId.values().stream().sorted(Comparator.comparing(Evidence::id)).toList();
        }

        private static AtlasDiagnostic diagnostic(String code, String message, SourceLocator locator) {
            return new AtlasDiagnostic(
                    AtlasId.of("diagnostic:resolver:" + encode(code + ":" + message)),
                    DiagnosticSeverity.WARNING,
                    code,
                    message,
                    Optional.of(locator));
        }

        private static String encode(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
