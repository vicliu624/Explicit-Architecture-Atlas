package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.ConfigurationScenarioResolveUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BeanDefinitionOverridePolicy;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentDefinition;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionTerm;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionPoint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioComponentStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInjectionResolution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInvocationResolution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInvocationStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioResolution;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 将可能 Bean 图解析为指定 Profile、有效属性和 Classpath 下的场景化组件图。
 *
 * <p>该解析器不会执行用户的 {@code Condition#matches}，也不会读取秘密或启动 Spring。自定义条件、缺失属性和
 * 红acted属性都保留为 {@link ConditionStatus#UNRESOLVED}；因此输出不会把静态候选误写成实际运行目标。</p>
 */
public final class DefaultConfigurationScenarioResolver implements ConfigurationScenarioResolveUseCase {

    @Override
    public ScenarioResolution resolve(AtlasPackage atlasPackage, ConfigurationScenario scenario) {
        Objects.requireNonNull(atlasPackage, "atlasPackage 不能为空");
        Objects.requireNonNull(scenario, "scenario 不能为空");
        List<ComponentDefinition> components = atlasPackage.designAtlas().nodes().stream()
                .filter(ComponentDefinition.class::isInstance)
                .map(ComponentDefinition.class::cast)
                .sorted(Comparator.comparing(ComponentDefinition::id))
                .toList();
        ApplicationContextScope context = scenario.applicationContext();
        ContextModuleClosure moduleClosure = ContextModuleClosure.from(atlasPackage, context);
        ContextApplicationComposition composition = ContextApplicationComposition.from(atlasPackage, context);
        List<ComponentDefinition> contextComponents = components.stream()
                .filter(component -> belongsToContext(component, context, moduleClosure, composition))
                .toList();
        Map<AtlasId, ComponentEvaluation> evaluations = new LinkedHashMap<>();
        for (ComponentDefinition component : components) {
            if (!belongsToContext(component, context, moduleClosure, composition)) {
                evaluations.put(component.id(), new ComponentEvaluation(ConditionStatus.EXCLUDED,
                        "不属于应用上下文 " + context.contextId() + " 的可见源码/扫描边界"));
            }
        }
        TypeHierarchy hierarchy = TypeHierarchy.from(atlasPackage);
        evaluations.putAll(evaluateComponents(contextComponents, scenario, hierarchy));
        List<AtlasDiagnostic> diagnostics = new ArrayList<>();
        moduleClosure.unresolvedReason().ifPresent(reason -> diagnostics.add(diagnostic(
                "APPLICATION_CONTEXT_MODULE_UNRESOLVED", reason, scenario)));
        composition.unresolvedReason().ifPresent(reason -> diagnostics.add(diagnostic(
                "APPLICATION_CONTEXT_COMPOSITION_UNRESOLVED", reason, scenario)));
        evaluations = resolveBeanDefinitionOverrides(contextComponents, evaluations, scenario, diagnostics);
        final Map<AtlasId, ComponentEvaluation> finalEvaluations = evaluations;
        Set<AtlasId> contextComponentIds = contextComponents.stream().map(ComponentDefinition::id).collect(java.util.stream.Collectors.toSet());
        List<InjectionPoint> injections = atlasPackage.designAtlas().nodes().stream()
                .filter(InjectionPoint.class::isInstance)
                .map(InjectionPoint.class::cast)
                .filter(injection -> contextComponentIds.contains(injection.ownerComponentId()))
                .sorted(Comparator.comparing(InjectionPoint::id))
                .toList();
        List<ScenarioInjectionResolution> resolutions = injections.stream()
                .map(injection -> resolveInjection(injection, components, finalEvaluations, hierarchy, diagnostics))
                .toList();
        List<ScenarioComponentStatus> statuses = components.stream().map(component -> {
            ComponentEvaluation evaluation = finalEvaluations.get(component.id());
            return new ScenarioComponentStatus(component.id(), evaluation.status(), evaluation.reason());
        }).toList();
        List<ScenarioInvocationResolution> invocations = resolveInvocations(
                atlasPackage, components, injections, finalEvaluations, resolutions);
        return new ScenarioResolution(scenario, statuses, resolutions, invocations, diagnostics);
    }

    /**
     * 将已经解析出的注入结果投影到静态 {@code CALLS} 边。只有调用接收者能同一个 Bean 注入点关联、选中 Bean
     * 唯一且能在 Package 内定位其实现方法时，才产生 {@link ScenarioInvocationStatus#RESOLVED}。
     * 其余调用保留静态边并说明为何没有冒充运行时结论。
     */
    private static List<ScenarioInvocationResolution> resolveInvocations(
            AtlasPackage atlasPackage,
            List<ComponentDefinition> components,
            List<InjectionPoint> injections,
            Map<AtlasId, ComponentEvaluation> evaluations,
            List<ScenarioInjectionResolution> injectionResolutions) {
        Map<AtlasId, ScenarioInjectionResolution> byInjection = injectionResolutions.stream()
                .collect(java.util.stream.Collectors.toMap(ScenarioInjectionResolution::injectionPointId, value -> value,
                        (first, ignored) -> first, LinkedHashMap::new));
        InvocationIndex index = InvocationIndex.from(atlasPackage, components, injections);
        List<AtlasRelation> calls = atlasPackage.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.CALLS)
                // 一个 Package 可以包含多个可启动应用。场景 API 不返回其它进程的调用边，避免“在同仓库”被误解为
                // “在当前 ApplicationContext 可执行”。未决 owner 仍保留，以便把配置不充分解释为未决而非消失。
                .filter(relation -> index.owners(relation.sourceId()).stream()
                        .map(ComponentDefinition::id).map(evaluations::get).filter(Objects::nonNull)
                        .anyMatch(evaluation -> evaluation.status() != ConditionStatus.EXCLUDED))
                .sorted(Comparator.comparing(AtlasRelation::sourceId).thenComparing(AtlasRelation::targetId)).toList();
        return calls.stream().map(call -> resolveInvocation(call, index, evaluations, byInjection)).toList();
    }

    private static ScenarioInvocationResolution resolveInvocation(
            AtlasRelation call,
            InvocationIndex index,
            Map<AtlasId, ComponentEvaluation> evaluations,
            Map<AtlasId, ScenarioInjectionResolution> injectionResolutions) {
        CodeElement caller = index.code(call.sourceId());
        CodeElement staticTarget = index.code(call.targetId());
        if (caller == null || staticTarget == null || staticTarget.elementKind() != CodeElementKind.METHOD) {
            return invocation(call, ScenarioInvocationStatus.STATIC_ONLY, List.of(), List.of(), List.of(), List.of(), List.of(),
                    "静态 CALLS 边没有可定位的方法目标，未推断运行时分派");
        }
        List<ComponentDefinition> owners = index.owners(caller.id());
        if (owners.isEmpty()) {
            return invocation(call, ScenarioInvocationStatus.STATIC_ONLY, List.of(), List.of(), List.of(), List.of(), List.of(),
                    "调用方不属于已识别的容器组件；保留静态调用，不声明有效 Bean 目标");
        }
        List<ComponentDefinition> activeOwners = owners.stream()
                .filter(owner -> evaluations.get(owner.id()).status() == ConditionStatus.ACTIVE).toList();
        if (activeOwners.isEmpty()) {
            boolean unresolvedOwner = owners.stream().anyMatch(owner -> evaluations.get(owner.id()).status() == ConditionStatus.UNRESOLVED);
            return invocation(call, unresolvedOwner ? ScenarioInvocationStatus.UNRESOLVED : ScenarioInvocationStatus.EXCLUDED,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    unresolvedOwner ? "调用方组件的激活状态未决" : "调用方组件在当前场景未激活");
        }
        String receiver = receiverName(text(call.attributes(), "calleeExpression"));
        List<InjectionPoint> receiverInjections = receiverInjections(activeOwners, receiver, staticTarget, index);
        if (receiverInjections.isEmpty()) {
            return invocation(call, ScenarioInvocationStatus.STATIC_ONLY, List.of(), List.of(), List.of(), List.of(), List.of(),
                    receiver.isBlank()
                            ? "调用没有可识别的实例接收者；保留静态调用，不声明容器分派"
                            : "接收者 " + receiver + " 未与调用方的 Bean 注入点匹配；保留静态调用");
        }
        List<AtlasId> injectionIds = receiverInjections.stream().map(InjectionPoint::id).toList();
        List<ScenarioInjectionResolution> resolutions = receiverInjections.stream()
                .map(injection -> injectionResolutions.get(injection.id())).filter(Objects::nonNull).toList();
        if (resolutions.size() != receiverInjections.size() || resolutions.stream()
                .anyMatch(resolution -> resolution.status() != ConditionStatus.ACTIVE)) {
            return invocation(call, ScenarioInvocationStatus.UNRESOLVED, injectionIds,
                    resolutions.stream().flatMap(resolution -> resolution.candidates().stream()).toList(),
                    resolutions.stream().flatMap(resolution -> resolution.selected().stream()).toList(), List.of(), List.of(),
                    "接收者注入在当前场景无法确定为活动且唯一的绑定");
        }
        List<AtlasId> candidates = resolutions.stream().flatMap(resolution -> resolution.candidates().stream()).distinct().toList();
        List<AtlasId> selected = resolutions.stream().flatMap(resolution -> resolution.selected().stream()).distinct().toList();
        if (selected.size() != 1) {
            return invocation(call, ScenarioInvocationStatus.UNRESOLVED, injectionIds, candidates, selected, List.of(), List.of(),
                    selected.isEmpty() ? "注入未选择唯一 Bean（可能为 Optional、Provider 或无满足候选）"
                            : "多个 Bean 可能承接该调用，未声明单一实际实现");
        }
        ComponentDefinition targetComponent = index.component(selected.getFirst());
        if (targetComponent == null) {
            return invocation(call, ScenarioInvocationStatus.UNRESOLVED, injectionIds, candidates, selected, List.of(), List.of(),
                    "已选择的 Bean 不存在于当前组件定义图");
        }
        List<CodeElement> implementationMethods = index.implementationMethods(targetComponent.id(), staticTarget);
        List<AtlasId> advisors = index.declaredAdvisors(selected, implementationMethods, staticTarget);
        if (implementationMethods.size() != 1) {
            return invocation(call, ScenarioInvocationStatus.UNRESOLVED, injectionIds, candidates, selected,
                    implementationMethods.stream().map(CodeElement::id).toList(), advisors,
                    implementationMethods.isEmpty()
                            ? "已选择 Bean，但 Package 中没有能唯一匹配的本地实现方法（可能来自依赖、代理或动态生成）"
                            : "已选择 Bean 存在多个同名实现方法，缺少可靠重载分派信息");
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("接收者 " + receiver + " 绑定到唯一活动 Bean " + targetComponent.beanName());
        reasons.add("按组件候选类型和方法签名定位本地实现");
        if (!advisors.isEmpty()) {
            reasons.add("发现源码声明的 Advisor；实际代理链仍应由运行时证据验证");
        }
        return invocation(call, ScenarioInvocationStatus.RESOLVED, injectionIds, candidates, selected,
                implementationMethods.stream().map(CodeElement::id).toList(), advisors, reasons.toArray(String[]::new));
    }

    private static ScenarioInvocationResolution invocation(
            AtlasRelation call,
            ScenarioInvocationStatus status,
            List<AtlasId> injectionIds,
            List<AtlasId> candidates,
            List<AtlasId> selected,
            List<AtlasId> effectiveTargets,
            List<AtlasId> advisors,
            String... reasons) {
        return new ScenarioInvocationResolution(call.sourceId(), call.targetId(), status, injectionIds, candidates, selected,
                effectiveTargets, advisors, List.of(reasons));
    }

    private static List<InjectionPoint> receiverInjections(
            List<ComponentDefinition> activeOwners,
            String receiver,
            CodeElement staticTarget,
            InvocationIndex index) {
        List<InjectionPoint> matches = new ArrayList<>();
        for (ComponentDefinition owner : activeOwners) {
            List<InjectionPoint> ownerInjections = receiver.isBlank()
                    ? index.injections(owner.id())
                    : index.injections(owner.id(), receiver);
            ownerInjections.stream().filter(injection -> matchesReceiver(injection, receiver, staticTarget)).forEach(matches::add);
        }
        return matches.stream().distinct().sorted(Comparator.comparing(InjectionPoint::id)).toList();
    }

    private static boolean matchesReceiver(InjectionPoint injection, String receiver, CodeElement staticTarget) {
        if (!receiver.isBlank()) {
            return receiver.equals(injection.name());
        }
        // 只有接收者语法缺失时才按声明类型回退，避免把局部变量误认成字段或构造器注入。
        return sameType(injection.requestedType(), methodOwner(staticTarget.qualifiedName()));
    }

    /**
     * 场景调用解析所需的反向索引。
     *
     * <p>真实工程通常有数万至数十万条静态 {@code CALLS} 边。不能对每一条边重新扫描所有组件、注入点、
     * 代码元素和 Advisor，否则“查看一个场景”会退化成 O(calls × components × code) 的全图笛卡尔积。这里的
     * 索引只从 Package 已有事实生成，不会增加任何猜测；它只是把相同的判定改为按接收者和方法签名的局部查找。</p>
     */
    private static final class InvocationIndex {

        private final Map<AtlasId, CodeElement> codeById;
        private final Map<AtlasId, ComponentDefinition> componentsById;
        private final Map<AtlasId, List<ComponentDefinition>> ownersByMethodId;
        private final Map<AtlasId, List<InjectionPoint>> injectionsByOwnerId;
        private final Map<InjectionOwnerMember, List<InjectionPoint>> injectionsByOwnerAndMember;
        private final Map<ComponentMethod, List<CodeElement>> implementationMethods;
        private final Map<AtlasId, List<AtlasId>> advisorsByTarget;

        private InvocationIndex(
                Map<AtlasId, CodeElement> codeById,
                Map<AtlasId, ComponentDefinition> componentsById,
                Map<AtlasId, List<ComponentDefinition>> ownersByMethodId,
                Map<AtlasId, List<InjectionPoint>> injectionsByOwnerId,
                Map<InjectionOwnerMember, List<InjectionPoint>> injectionsByOwnerAndMember,
                Map<ComponentMethod, List<CodeElement>> implementationMethods,
                Map<AtlasId, List<AtlasId>> advisorsByTarget) {
            this.codeById = Map.copyOf(codeById);
            this.componentsById = Map.copyOf(componentsById);
            this.ownersByMethodId = immutableComponentLists(ownersByMethodId);
            this.injectionsByOwnerId = immutableInjectionLists(injectionsByOwnerId);
            this.injectionsByOwnerAndMember = immutableInjectionLists(injectionsByOwnerAndMember);
            this.implementationMethods = immutableCodeLists(implementationMethods);
            this.advisorsByTarget = immutableIdLists(advisorsByTarget);
        }

        static InvocationIndex from(
                AtlasPackage atlasPackage,
                List<ComponentDefinition> components,
                List<InjectionPoint> injections) {
            Map<AtlasId, CodeElement> code = atlasPackage.designAtlas().nodes().stream()
                    .filter(CodeElement.class::isInstance).map(CodeElement.class::cast)
                    .collect(java.util.stream.Collectors.toMap(CodeElement::id, value -> value, (first, ignored) -> first,
                            LinkedHashMap::new));
            Map<AtlasId, ComponentDefinition> componentsById = components.stream()
                    .collect(java.util.stream.Collectors.toMap(ComponentDefinition::id, value -> value, (first, ignored) -> first,
                            LinkedHashMap::new));
            Map<AtlasId, List<AtlasId>> definedCodeIds = new LinkedHashMap<>();
            Map<AtlasId, List<AtlasId>> advisorsByTarget = new LinkedHashMap<>();
            for (AtlasRelation relation : atlasPackage.designAtlas().relations()) {
                if (relation.kind() == RelationKind.DEFINES_BEAN) {
                    definedCodeIds.computeIfAbsent(relation.sourceId(), ignored -> new ArrayList<>()).add(relation.targetId());
                } else if (relation.kind() == RelationKind.INTERCEPTS) {
                    advisorsByTarget.computeIfAbsent(relation.targetId(), ignored -> new ArrayList<>()).add(relation.sourceId());
                }
            }

            Map<String, List<ComponentDefinition>> componentsByImplementationType = new LinkedHashMap<>();
            for (ComponentDefinition component : components) {
                implementationTypes(component, definedCodeIds, code)
                        .forEach(type -> componentsByImplementationType
                                .computeIfAbsent(normalize(type), ignored -> new ArrayList<>()).add(component));
            }

            Map<AtlasId, List<ComponentDefinition>> ownersByMethod = new LinkedHashMap<>();
            Map<ComponentMethod, List<CodeElement>> methodsByComponent = new LinkedHashMap<>();
            for (CodeElement method : code.values()) {
                if (method.elementKind() != CodeElementKind.METHOD) {
                    continue;
                }
                List<ComponentDefinition> owners = componentsForType(
                        methodOwner(method.qualifiedName()), componentsByImplementationType);
                if (owners.isEmpty()) {
                    continue;
                }
                ownersByMethod.put(method.id(), owners);
                MethodSignature signature = MethodSignature.of(method);
                for (ComponentDefinition owner : owners) {
                    methodsByComponent.computeIfAbsent(new ComponentMethod(owner.id(), signature), ignored -> new ArrayList<>()).add(method);
                }
            }

            Map<AtlasId, List<InjectionPoint>> injectionsByOwner = new LinkedHashMap<>();
            Map<InjectionOwnerMember, List<InjectionPoint>> injectionsByOwnerAndMember = new LinkedHashMap<>();
            for (InjectionPoint injection : injections) {
                injectionsByOwner.computeIfAbsent(injection.ownerComponentId(), ignored -> new ArrayList<>()).add(injection);
                injectionsByOwnerAndMember.computeIfAbsent(
                        new InjectionOwnerMember(injection.ownerComponentId(), injection.name()), ignored -> new ArrayList<>()).add(injection);
            }
            return new InvocationIndex(
                    code, componentsById, ownersByMethod, injectionsByOwner, injectionsByOwnerAndMember,
                    methodsByComponent, advisorsByTarget);
        }

        CodeElement code(AtlasId codeId) {
            return codeById.get(codeId);
        }

        ComponentDefinition component(AtlasId componentId) {
            return componentsById.get(componentId);
        }

        List<ComponentDefinition> owners(AtlasId methodId) {
            return ownersByMethodId.getOrDefault(methodId, List.of());
        }

        List<InjectionPoint> injections(AtlasId ownerId) {
            return injectionsByOwnerId.getOrDefault(ownerId, List.of());
        }

        List<InjectionPoint> injections(AtlasId ownerId, String memberName) {
            return injectionsByOwnerAndMember.getOrDefault(new InjectionOwnerMember(ownerId, memberName), List.of());
        }

        List<CodeElement> implementationMethods(AtlasId componentId, CodeElement staticTarget) {
            return implementationMethods.getOrDefault(new ComponentMethod(componentId, MethodSignature.of(staticTarget)), List.of());
        }

        List<AtlasId> declaredAdvisors(
                List<AtlasId> selectedComponents,
                List<CodeElement> effectiveTargets,
                CodeElement staticTarget) {
            Set<AtlasId> advisors = new LinkedHashSet<>();
            for (AtlasId component : selectedComponents) {
                advisors.addAll(advisorsByTarget.getOrDefault(component, List.of()));
            }
            for (CodeElement target : effectiveTargets) {
                advisors.addAll(advisorsByTarget.getOrDefault(target.id(), List.of()));
            }
            advisors.addAll(advisorsByTarget.getOrDefault(staticTarget.id(), List.of()));
            return advisors.stream().sorted().toList();
        }

        private static List<String> implementationTypes(
                ComponentDefinition component,
                Map<AtlasId, List<AtlasId>> definedCodeIds,
                Map<AtlasId, CodeElement> code) {
            List<String> types = new ArrayList<>(List.of(component.candidateType()));
            // declaredType 可能是接口。仅在它本来就是 candidateType 时，才能把它当作实现方法的拥有者。
            if (sameType(component.declaredType(), component.candidateType())) {
                types.add(component.declaredType());
            }
            definedCodeIds.getOrDefault(component.id(), List.of()).stream()
                    .map(code::get).filter(Objects::nonNull).map(CodeElement::qualifiedName).forEach(types::add);
            return types.stream().map(DefaultConfigurationScenarioResolver::normalize)
                    .filter(type -> !type.isBlank()).distinct().toList();
        }

        private static List<ComponentDefinition> componentsForType(
                String ownerType,
                Map<String, List<ComponentDefinition>> componentsByImplementationType) {
            String normalized = normalize(ownerType);
            List<ComponentDefinition> exact = componentsByImplementationType.getOrDefault(normalized, List.of());
            if (!exact.isEmpty()) {
                return exact.stream().distinct().sorted(Comparator.comparing(ComponentDefinition::id)).toList();
            }
            // Source facts 有时只能得到简单类型名；此时才使用同样保守的简单名退化，不让两个完整限定名误合并。
            if (!normalized.contains(".")) {
                return componentsByImplementationType.entrySet().stream()
                        .filter(entry -> simpleName(entry.getKey()).equals(normalized))
                        .flatMap(entry -> entry.getValue().stream()).distinct()
                        .sorted(Comparator.comparing(ComponentDefinition::id)).toList();
            }
            return List.of();
        }

        private static Map<AtlasId, List<ComponentDefinition>> immutableComponentLists(
                Map<AtlasId, List<ComponentDefinition>> values) {
            Map<AtlasId, List<ComponentDefinition>> normalized = new LinkedHashMap<>();
            values.forEach((key, entries) -> normalized.put(key, entries.stream().distinct()
                    .sorted(Comparator.comparing(ComponentDefinition::id)).toList()));
            return Map.copyOf(normalized);
        }

        private static <K> Map<K, List<InjectionPoint>> immutableInjectionLists(Map<K, List<InjectionPoint>> values) {
            Map<K, List<InjectionPoint>> normalized = new LinkedHashMap<>();
            values.forEach((key, entries) -> normalized.put(key, entries.stream().distinct()
                    .sorted(Comparator.comparing(InjectionPoint::id)).toList()));
            return Map.copyOf(normalized);
        }

        private static Map<ComponentMethod, List<CodeElement>> immutableCodeLists(
                Map<ComponentMethod, List<CodeElement>> values) {
            Map<ComponentMethod, List<CodeElement>> normalized = new LinkedHashMap<>();
            values.forEach((key, entries) -> normalized.put(key, entries.stream().distinct()
                    .sorted(Comparator.comparing(CodeElement::id)).toList()));
            return Map.copyOf(normalized);
        }

        private static Map<AtlasId, List<AtlasId>> immutableIdLists(Map<AtlasId, List<AtlasId>> values) {
            Map<AtlasId, List<AtlasId>> normalized = new LinkedHashMap<>();
            values.forEach((key, entries) -> normalized.put(key, entries.stream().distinct().sorted().toList()));
            return Map.copyOf(normalized);
        }
    }

    private record InjectionOwnerMember(AtlasId ownerId, String memberName) {
        private InjectionOwnerMember {
            Objects.requireNonNull(ownerId, "ownerId 不能为空");
            memberName = Objects.requireNonNull(memberName, "memberName 不能为空").trim();
        }
    }

    private record ComponentMethod(AtlasId componentId, MethodSignature signature) {
        private ComponentMethod {
            Objects.requireNonNull(componentId, "componentId 不能为空");
            Objects.requireNonNull(signature, "signature 不能为空");
        }
    }

    private record MethodSignature(String name, int parameterCount) {
        private MethodSignature {
            name = Objects.requireNonNull(name, "name 不能为空").trim();
            if (name.isEmpty() || parameterCount < 0) {
                throw new IllegalArgumentException("方法签名不合法");
            }
        }

        static MethodSignature of(CodeElement method) {
            return new MethodSignature(
                    methodName(method.qualifiedName()), DefaultConfigurationScenarioResolver.parameterCount(method.qualifiedName()));
        }
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

    private static String methodOwner(String qualifiedMethod) {
        int separator = qualifiedMethod.lastIndexOf('#');
        return separator < 0 ? "" : qualifiedMethod.substring(0, separator);
    }

    private static String methodName(String qualifiedMethod) {
        int separator = qualifiedMethod.lastIndexOf('#');
        int parameters = qualifiedMethod.indexOf('(', Math.max(0, separator));
        return separator < 0 ? "" : qualifiedMethod.substring(separator + 1, parameters < 0 ? qualifiedMethod.length() : parameters);
    }

    private static int parameterCount(String qualifiedMethod) {
        int start = qualifiedMethod.indexOf('(');
        int end = qualifiedMethod.lastIndexOf(')');
        if (start < 0 || end <= start + 1) {
            return 0;
        }
        return qualifiedMethod.substring(start + 1, end).split(",", -1).length;
    }

    private static boolean sameType(String first, String second) {
        String left = normalize(first);
        String right = normalize(second);
        return left.equals(right) || (!left.contains(".") || !right.contains(".")) && simpleName(left).equals(simpleName(right));
    }

    private static String text(Map<String, AtlasValue> attributes, String key) {
        AtlasValue value = attributes.get(key);
        return value instanceof AtlasValue.Text text ? text.value().trim() : "";
    }

    private static List<String> values(Map<String, AtlasValue> attributes, String key) {
        AtlasValue value = attributes.get(key);
        if (value instanceof AtlasValue.Values values) {
            return values.values().stream().filter(AtlasValue.Text.class::isInstance).map(AtlasValue.Text.class::cast)
                    .map(AtlasValue.Text::value).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        String single = text(attributes, key);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private static Map<AtlasId, ComponentEvaluation> evaluateComponents(
            List<ComponentDefinition> components,
            ConfigurationScenario scenario,
            TypeHierarchy hierarchy) {
        Map<AtlasId, ComponentEvaluation> evaluations = new LinkedHashMap<>();
        for (ComponentDefinition component : components) {
            evaluations.put(component.id(), evaluateNonBeanTerms(component, scenario));
        }
        // ConditionalOnBean/ConditionalOnMissingBean 依赖其它候选的激活状态，进行有限不动点解析。
        Set<String> seenStates = new LinkedHashSet<>();
        for (int pass = 0; pass < Math.max(1, components.size() * 2 + 2); pass++) {
            String state = stateOf(evaluations);
            if (!seenStates.add(state)) {
                return unresolvedBeanConditionCycle(components, evaluations);
            }
            boolean changed = false;
            Map<AtlasId, ComponentEvaluation> next = new LinkedHashMap<>();
            for (ComponentDefinition component : components) {
                ComponentEvaluation base = evaluateNonBeanTerms(component, scenario);
                ComponentEvaluation evaluated = base.status() == ConditionStatus.EXCLUDED
                        ? base
                        : evaluateBeanTerms(component, components, evaluations, base, hierarchy);
                next.put(component.id(), evaluated);
                // 不动点只由激活状态决定；首次补充的解释文本不能被误识别为 Bean 条件循环。
                changed |= evaluated.status() != evaluations.get(component.id()).status();
            }
            evaluations = next;
            if (!changed) {
                return Map.copyOf(evaluations);
            }
        }
        return unresolvedBeanConditionCycle(components, evaluations);
    }

    /**
     * 在条件求值完成后处理同名 Bean。Spring 的“允许覆盖”并不等于分析器可以按文件遍历顺序挑一个；只有 Workspace
     * 或 Runtime Overlay 明确提供获胜候选时才把其它定义排除。否则上下文的启动结果和后续调用分派都保持未决。
     */
    private static Map<AtlasId, ComponentEvaluation> resolveBeanDefinitionOverrides(
            List<ComponentDefinition> components,
            Map<AtlasId, ComponentEvaluation> initial,
            ConfigurationScenario scenario,
            List<AtlasDiagnostic> diagnostics) {
        Map<AtlasId, ComponentEvaluation> resolved = new LinkedHashMap<>(initial);
        Map<String, List<ComponentDefinition>> activeByName = components.stream()
                .filter(component -> initial.get(component.id()).status() == ConditionStatus.ACTIVE)
                .collect(java.util.stream.Collectors.groupingBy(ComponentDefinition::beanName, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        ApplicationContextScope context = scenario.applicationContext();
        for (Map.Entry<String, List<ComponentDefinition>> entry : activeByName.entrySet()) {
            String beanName = entry.getKey();
            List<ComponentDefinition> collisions = entry.getValue();
            if (collisions.size() < 2) {
                continue;
            }
            String declaredWinner = context.explicitBeanOverrides().get(beanName);
            if (context.beanDefinitionOverridePolicy() == BeanDefinitionOverridePolicy.ALLOW && declaredWinner != null) {
                List<ComponentDefinition> winners = collisions.stream().filter(component -> sameType(component.candidateType(), declaredWinner)
                        || sameType(component.declaredType(), declaredWinner)).toList();
                if (winners.size() == 1) {
                    ComponentDefinition winner = winners.getFirst();
                    for (ComponentDefinition component : collisions) {
                        ComponentEvaluation prior = initial.get(component.id());
                        if (component.id().equals(winner.id())) {
                            resolved.put(component.id(), new ComponentEvaluation(ConditionStatus.ACTIVE,
                                    joinReason(prior.reason(), "同名 Bean 的显式覆盖映射选中 " + declaredWinner)));
                        } else {
                            resolved.put(component.id(), new ComponentEvaluation(ConditionStatus.EXCLUDED,
                                    joinReason(prior.reason(), "同名 Bean " + beanName + " 被显式覆盖为 " + declaredWinner)));
                        }
                    }
                    continue;
                }
                markBeanDefinitionCollision(collisions, resolved, diagnostics, beanName,
                        "显式覆盖目标 " + declaredWinner + " 未能唯一匹配活动定义");
                continue;
            }
            String reason = switch (context.beanDefinitionOverridePolicy()) {
                case FORBID -> "上下文禁止同名 Bean 覆盖，应用不能以此 Bean 图完成启动";
                case ALLOW -> "上下文允许覆盖，但没有经证实的注册顺序或 explicitBeanOverrides 获胜目标";
                case UNSPECIFIED -> "没有 Bean 覆盖策略证据，不能按源码扫描顺序选择同名 Bean";
            };
            markBeanDefinitionCollision(collisions, resolved, diagnostics, beanName, reason);
        }
        return Map.copyOf(resolved);
    }

    private static void markBeanDefinitionCollision(
            List<ComponentDefinition> collisions,
            Map<AtlasId, ComponentEvaluation> evaluations,
            List<AtlasDiagnostic> diagnostics,
            String beanName,
            String reason) {
        for (ComponentDefinition component : collisions) {
            ComponentEvaluation prior = evaluations.get(component.id());
            evaluations.put(component.id(), new ComponentEvaluation(ConditionStatus.UNRESOLVED,
                    joinReason(prior.reason(), "同名 Bean " + beanName + " 冲突：" + reason)));
        }
        diagnostics.add(diagnostic("BEAN_DEFINITION_OVERRIDE_UNRESOLVED",
                "Bean " + beanName + " 有 " + collisions.size() + " 个活动定义；" + reason, collisions.getFirst()));
    }

    private static boolean belongsToContext(
            ComponentDefinition component,
            ApplicationContextScope context,
            ContextModuleClosure moduleClosure,
            ContextApplicationComposition composition) {
        if (!moduleClosure.contains(component)) {
            return false;
        }
        boolean sourceMatches = context.sourcePathPrefixes().isEmpty() || component.evidence().stream()
                .map(evidence -> projectRelativePath(evidence.locator().uri()))
                .flatMap(Optional::stream)
                .anyMatch(path -> context.sourcePathPrefixes().stream().anyMatch(path::startsWith));
        if (!sourceMatches) {
            return false;
        }
        if (!composition.allows(component)) {
            return false;
        }
        // @Bean 的产品类型常来自第三方包（DataSource、Feign Client 等），却由当前上下文里的配置类创建。因此仅当
        // 它是 stereotype 扫描定义时才以候选类型收紧扫描包；工厂方法已由 sourcePathPrefixes 证明归属。
        if (component.componentKind() == ComponentKind.FACTORY_METHOD || context.componentScanPackages().isEmpty()) {
            return true;
        }
        return context.componentScanPackages().stream().anyMatch(prefix -> typeInPackage(component.candidateType(), prefix)
                || typeInPackage(component.declaredType(), prefix));
    }

    private static boolean typeInPackage(String type, String packagePrefix) {
        String normalizedType = normalize(type);
        String normalizedPrefix = packagePrefix == null ? "" : packagePrefix.trim();
        return normalizedType.equals(normalizedPrefix) || normalizedType.startsWith(normalizedPrefix + ".");
    }

    private static Optional<String> projectRelativePath(String uri) {
        if (uri == null || !uri.startsWith("project://")) {
            return Optional.empty();
        }
        int slash = uri.indexOf('/', "project://".length());
        if (slash < 0 || slash == uri.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(uri.substring(slash + 1));
    }

    /**
     * 用 Maven 原始事实（不是文件遍历次序）还原一个启动模块的本地反应堆依赖闭包。第三方依赖没有项目源码时不会伪造
     * 成本地 Bean；它们会在随后候选/调用解析中以外部或未决目标呈现。
     */
    private static final class ContextModuleClosure {

        private final boolean requested;
        private final List<String> sourcePrefixes;
        private final String unresolvedReason;

        private ContextModuleClosure(boolean requested, List<String> sourcePrefixes, String unresolvedReason) {
            this.requested = requested;
            this.sourcePrefixes = List.copyOf(sourcePrefixes);
            this.unresolvedReason = unresolvedReason == null ? "" : unresolvedReason;
        }

        static ContextModuleClosure from(AtlasPackage atlasPackage, ApplicationContextScope context) {
            if (context.rootModuleCoordinates().isEmpty()) {
                return new ContextModuleClosure(false, List.of(), "");
            }
            Map<AtlasId, SourceFact> factsById = atlasPackage.facts().facts().stream()
                    .collect(java.util.stream.Collectors.toMap(SourceFact::id, fact -> fact, (first, ignored) -> first,
                            LinkedHashMap::new));
            Map<String, List<SourceFact>> modulesByCoordinate = atlasPackage.facts().facts().stream()
                    .filter(fact -> fact.kind() == FactKind.MAVEN_MODULE)
                    .collect(java.util.stream.Collectors.groupingBy(SourceFact::subject, LinkedHashMap::new,
                            java.util.stream.Collectors.toList()));
            List<AtlasId> pending = new ArrayList<>();
            List<String> missingRoots = new ArrayList<>();
            for (String root : context.rootModuleCoordinates()) {
                List<SourceFact> modules = modulesByCoordinate.getOrDefault(root, List.of());
                if (modules.isEmpty()) {
                    missingRoots.add(root);
                } else {
                    modules.stream().map(SourceFact::id).forEach(pending::add);
                }
            }
            Set<AtlasId> visited = new LinkedHashSet<>();
            Set<String> reachableCoordinates = new LinkedHashSet<>();
            while (!pending.isEmpty()) {
                AtlasId moduleId = pending.removeFirst();
                if (!visited.add(moduleId)) {
                    continue;
                }
                SourceFact module = factsById.get(moduleId);
                if (module == null || module.kind() != FactKind.MAVEN_MODULE) {
                    continue;
                }
                reachableCoordinates.add(module.subject());
                atlasPackage.facts().relations().stream().filter(relation -> relation.sourceFactId().equals(moduleId)
                                && relation.kind() == RelationKind.DEPENDS_ON)
                        .map(relation -> factsById.get(relation.targetFactId())).filter(Objects::nonNull)
                        .flatMap(dependency -> modulesByCoordinate.getOrDefault(dependency.subject(), List.of()).stream())
                        .map(SourceFact::id).forEach(pending::add);
            }
            List<String> prefixes = atlasPackage.facts().facts().stream()
                    .filter(fact -> fact.kind() == FactKind.MAVEN_MODULE && reachableCoordinates.contains(fact.subject()))
                    .map(fact -> text(fact.attributes(), "pom"))
                    .filter(path -> !path.isBlank() && path.endsWith("/pom.xml"))
                    .map(path -> path.substring(0, path.length() - "pom.xml".length()))
                    .distinct().sorted().toList();
            String reason = missingRoots.isEmpty() && !prefixes.isEmpty() ? ""
                    : "应用上下文 " + context.contextId() + " 无法从 Maven 事实解析根模块闭包"
                    + (missingRoots.isEmpty() ? "" : "；缺失根模块: " + String.join(",", missingRoots));
            return new ContextModuleClosure(true, prefixes, reason);
        }

        boolean contains(ComponentDefinition component) {
            if (!requested) {
                return true;
            }
            return component.evidence().stream().map(evidence -> projectRelativePath(evidence.locator().uri()))
                    .flatMap(Optional::stream).anyMatch(path -> sourcePrefixes.stream().anyMatch(path::startsWith));
        }

        Optional<String> unresolvedReason() {
            return unresolvedReason.isBlank() ? Optional.empty() : Optional.of(unresolvedReason);
        }
    }

    /** 从 {@code @SpringBootApplication(scanBasePackages=...)} 与 {@code @Import} 源码事实还原当前组成根。 */
    private static final class ContextApplicationComposition {

        private final boolean proven;
        private final List<String> scanPackages;
        private final List<String> importedTypes;
        private final String unresolvedReason;

        private ContextApplicationComposition(
                boolean proven,
                List<String> scanPackages,
                List<String> importedTypes,
                String unresolvedReason) {
            this.proven = proven;
            this.scanPackages = List.copyOf(scanPackages);
            this.importedTypes = List.copyOf(importedTypes);
            this.unresolvedReason = unresolvedReason == null ? "" : unresolvedReason;
        }

        static ContextApplicationComposition from(AtlasPackage atlasPackage, ApplicationContextScope context) {
            if (context.entryPointTypes().isEmpty()) {
                return new ContextApplicationComposition(false, List.of(), List.of(), "");
            }
            List<SourceFact> compositions = atlasPackage.facts().facts().stream()
                    .filter(fact -> fact.kind() == FactKind.APPLICATION_COMPOSITION)
                    .filter(fact -> context.entryPointTypes().stream().anyMatch(entry -> sameType(entry, fact.subject())))
                    .toList();
            if (compositions.isEmpty()) {
                return new ContextApplicationComposition(false, List.of(), List.of(),
                        "应用上下文 " + context.contextId() + " 未找到入口类型的 Spring 组成事实: "
                                + String.join(",", context.entryPointTypes()));
            }
            List<String> scans = compositions.stream().flatMap(fact -> values(fact.attributes(), "componentScanPackages").stream())
                    .distinct().sorted().toList();
            List<String> imports = compositions.stream().flatMap(fact -> values(fact.attributes(), "importedTypes").stream())
                    .map(DefaultConfigurationScenarioResolver::normalize).distinct().sorted().toList();
            return new ContextApplicationComposition(true, scans, imports, "");
        }

        boolean allows(ComponentDefinition component) {
            if (!proven) {
                return true;
            }
            String definitionType = text(component.attributes(), "definitionType");
            if (definitionType.isBlank()) {
                definitionType = component.candidateType();
            }
            String normalizedDefinition = normalize(definitionType);
            return importedTypes.stream().anyMatch(imported -> sameType(imported, normalizedDefinition))
                    || scanPackages.stream().anyMatch(prefix -> typeInPackage(normalizedDefinition, prefix));
        }

        Optional<String> unresolvedReason() {
            return unresolvedReason.isBlank() ? Optional.empty() : Optional.of(unresolvedReason);
        }
    }

    private static ComponentEvaluation evaluateNonBeanTerms(ComponentDefinition component, ConfigurationScenario scenario) {
        List<String> reasons = new ArrayList<>();
        ConditionStatus result = ConditionStatus.ACTIVE;
        for (ConditionTerm term : component.activationCondition().terms()) {
            if (term.kind().equals("BEAN")) {
                continue;
            }
            ConditionStatus status = switch (term.kind()) {
                case "PROFILE" -> profileStatus(scenario, term);
                case "PROPERTY" -> propertyStatus(scenario, term);
                case "CLASSPATH" -> classpathStatus(scenario, term);
                case "CUSTOM" -> ConditionStatus.UNRESOLVED;
                default -> ConditionStatus.UNRESOLVED;
            };
            reasons.add(term.kind() + " " + term.subject() + " " + term.operator() + " " + term.expectedValue() + " => " + status);
            result = combine(result, status);
        }
        if (component.activationCondition().terms().isEmpty()) {
            reasons.add("无激活条件");
        }
        return new ComponentEvaluation(result, String.join("；", reasons));
    }

    private static ComponentEvaluation evaluateBeanTerms(
            ComponentDefinition component,
            List<ComponentDefinition> all,
            Map<AtlasId, ComponentEvaluation> evaluations,
            ComponentEvaluation base,
            TypeHierarchy hierarchy) {
        ConditionStatus result = base.status();
        List<String> reasons = new ArrayList<>();
        if (!base.reason().isBlank()) {
            reasons.add(base.reason());
        }
        for (ConditionTerm term : component.activationCondition().terms()) {
            if (!term.kind().equals("BEAN")) {
                continue;
            }
            List<ComponentDefinition> matches = all.stream()
                    .filter(candidate -> !candidate.id().equals(component.id()))
                    .filter(candidate -> hierarchy.compatible(term.subject(), candidate))
                    .toList();
            long active = matches.stream().filter(candidate -> evaluations.get(candidate.id()).status() == ConditionStatus.ACTIVE).count();
            long activePrimary = matches.stream()
                    .filter(candidate -> evaluations.get(candidate.id()).status() == ConditionStatus.ACTIVE)
                    .filter(ComponentDefinition::primary).count();
            long unresolved = matches.stream().filter(candidate -> evaluations.get(candidate.id()).status() == ConditionStatus.UNRESOLVED).count();
            ConditionStatus termStatus = switch (term.operator()) {
                case "PRESENT" -> active > 0 ? ConditionStatus.ACTIVE
                        : unresolved > 0 ? ConditionStatus.UNRESOLVED : ConditionStatus.EXCLUDED;
                case "ABSENT" -> active > 0 ? ConditionStatus.EXCLUDED
                        : unresolved > 0 ? ConditionStatus.UNRESOLVED : ConditionStatus.ACTIVE;
                // Spring Boot 的 OnBeanCondition 允许多个候选时由唯一 @Primary 形成单一可注入候选。
                case "SINGLE_CANDIDATE" -> active == 1 || activePrimary == 1 ? ConditionStatus.ACTIVE
                        : unresolved > 0 ? ConditionStatus.UNRESOLVED : ConditionStatus.EXCLUDED;
                default -> ConditionStatus.UNRESOLVED;
            };
            reasons.add("BEAN " + term.subject() + " " + term.operator() + " => " + termStatus);
            result = combine(result, termStatus);
        }
        return new ComponentEvaluation(result, String.join("；", reasons));
    }

    private static ConditionStatus propertyStatus(ConfigurationScenario scenario, ConditionTerm term) {
        Optional<io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty> value =
                Optional.ofNullable(scenario.properties().get(term.subject()));
        if (value.isEmpty()) {
            return term.operator().endsWith("_OR_MISSING") ? ConditionStatus.ACTIVE : ConditionStatus.EXCLUDED;
        }
        if (value.get().redacted()) {
            return ConditionStatus.UNRESOLVED;
        }
        String operator = term.operator().replace("_OR_MISSING", "");
        boolean matches = switch (operator) {
            case "EQUALS", "=" -> value.get().value().equals(term.expectedValue());
            case "NOT_EQUALS_IGNORE_CASE" -> !value.get().value().equalsIgnoreCase(term.expectedValue());
            default -> false;
        };
        return matches ? ConditionStatus.ACTIVE : ConditionStatus.EXCLUDED;
    }

    private static ConditionStatus profileStatus(ConfigurationScenario scenario, ConditionTerm term) {
        if (!term.operator().equals("MATCHES_ANY") && !term.operator().equals("MATCHES")) {
            return ConditionStatus.UNRESOLVED;
        }
        try {
            return new ProfileExpression(term.expectedValue(), scenario.activeProfiles()).matches()
                    ? ConditionStatus.ACTIVE : ConditionStatus.EXCLUDED;
        } catch (IllegalArgumentException invalidExpression) {
            return ConditionStatus.UNRESOLVED;
        }
    }

    private static ConditionStatus classpathStatus(ConfigurationScenario scenario, ConditionTerm term) {
        boolean present = scenario.classpathCapabilities().stream()
                .anyMatch(capability -> normalize(capability).equals(normalize(term.subject())));
        return switch (term.operator()) {
            case "PRESENT" -> present ? ConditionStatus.ACTIVE
                    : scenario.classpathCapabilityCompleteness() == io.github.vicliu624.explicitarchitecture.atlas.domain.ClasspathCapabilityCompleteness.COMPLETE
                    ? ConditionStatus.EXCLUDED : ConditionStatus.UNRESOLVED;
            case "ABSENT" -> present ? ConditionStatus.EXCLUDED
                    : scenario.classpathCapabilityCompleteness() == io.github.vicliu624.explicitarchitecture.atlas.domain.ClasspathCapabilityCompleteness.COMPLETE
                    ? ConditionStatus.ACTIVE : ConditionStatus.UNRESOLVED;
            default -> ConditionStatus.UNRESOLVED;
        };
    }

    private static ScenarioInjectionResolution resolveInjection(
            InjectionPoint injection,
            List<ComponentDefinition> components,
            Map<AtlasId, ComponentEvaluation> evaluations,
            TypeHierarchy hierarchy,
            List<AtlasDiagnostic> diagnostics) {
        List<ComponentDefinition> typeCandidates = components.stream()
                .filter(component -> evaluations.get(component.id()).status() == ConditionStatus.ACTIVE)
                .filter(component -> hierarchy.compatible(injection.requestedType(), component))
                .toList();
        List<ComponentDefinition> filtered = injection.qualifiers().isEmpty() ? typeCandidates : typeCandidates.stream()
                .filter(component -> injection.qualifiers().contains(component.beanName())
                        || component.qualifiers().stream().anyMatch(injection.qualifiers()::contains))
                .toList();
        List<AtlasId> candidates = filtered.stream().map(ComponentDefinition::id).toList();
        List<String> reasons = new ArrayList<>();
        reasons.add("活动且类型兼容候选: " + candidates.size());
        if (!injection.qualifiers().isEmpty()) {
            reasons.add("已应用 Qualifier: " + String.join(",", injection.qualifiers()));
        }
        if (injection.cardinality() == InjectionCardinality.COLLECTION || injection.cardinality() == InjectionCardinality.MAP) {
            return new ScenarioInjectionResolution(injection.id(), ConditionStatus.ACTIVE, candidates, candidates, reasons);
        }
        if (injection.cardinality() == InjectionCardinality.PROVIDER) {
            reasons.add("Provider/ObjectFactory 为延迟查找；当前不将候选写成注入时唯一实际目标");
            return new ScenarioInjectionResolution(injection.id(), ConditionStatus.ACTIVE, candidates, List.of(), reasons);
        }
        if (candidates.size() == 1) {
            return new ScenarioInjectionResolution(injection.id(), ConditionStatus.ACTIVE, candidates, candidates, reasons);
        }
        List<AtlasId> primaries = filtered.stream().filter(ComponentDefinition::primary).map(ComponentDefinition::id).toList();
        if (primaries.size() == 1) {
            reasons.add("按 @Primary 选择唯一候选");
            return new ScenarioInjectionResolution(injection.id(), ConditionStatus.ACTIVE, candidates, primaries, reasons);
        }
        boolean unresolvedCandidate = components.stream()
                .filter(component -> hierarchy.compatible(injection.requestedType(), component))
                .anyMatch(component -> evaluations.get(component.id()).status() == ConditionStatus.UNRESOLVED);
        if (candidates.isEmpty() && (injection.cardinality() == InjectionCardinality.OPTIONAL || !injection.required())) {
            reasons.add("可选注入且当前无活动候选");
            return new ScenarioInjectionResolution(injection.id(), ConditionStatus.ACTIVE, candidates, List.of(), reasons);
        }
        ConditionStatus status = unresolvedCandidate ? ConditionStatus.UNRESOLVED : ConditionStatus.EXCLUDED;
        reasons.add(candidates.isEmpty() ? "没有可选择 Bean" : "多个候选且没有唯一 @Primary");
        diagnostics.add(diagnostic(candidates.isEmpty() ? "UNSATISFIED_BEAN_INJECTION" : "AMBIGUOUS_BEAN_INJECTION",
                "注入点 " + injection.name() + " 在当前场景无法唯一解析", injection));
        return new ScenarioInjectionResolution(injection.id(), status, candidates, List.of(), reasons);
    }

    private static String stateOf(Map<AtlasId, ComponentEvaluation> evaluations) {
        return evaluations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().status()).collect(java.util.stream.Collectors.joining("|"));
    }

    private static Map<AtlasId, ComponentEvaluation> unresolvedBeanConditionCycle(
            List<ComponentDefinition> components,
            Map<AtlasId, ComponentEvaluation> evaluations) {
        Map<AtlasId, ComponentEvaluation> resolved = new LinkedHashMap<>();
        for (ComponentDefinition component : components) {
            ComponentEvaluation evaluation = evaluations.get(component.id());
            boolean hasBeanCondition = component.activationCondition().terms().stream().anyMatch(term -> term.kind().equals("BEAN"));
            resolved.put(component.id(), hasBeanCondition && evaluation.status() != ConditionStatus.EXCLUDED
                    ? new ComponentEvaluation(ConditionStatus.UNRESOLVED,
                    joinReason(evaluation.reason(), "依赖 Bean 条件的顺序或循环无法由静态场景唯一确定"))
                    : evaluation);
        }
        return Map.copyOf(resolved);
    }

    private static String joinReason(String first, String second) {
        return first == null || first.isBlank() ? second : first + "；" + second;
    }

    private static ConditionStatus combine(ConditionStatus first, ConditionStatus second) {
        if (first == ConditionStatus.EXCLUDED || second == ConditionStatus.EXCLUDED) {
            return ConditionStatus.EXCLUDED;
        }
        if (first == ConditionStatus.UNRESOLVED || second == ConditionStatus.UNRESOLVED) {
            return ConditionStatus.UNRESOLVED;
        }
        return ConditionStatus.ACTIVE;
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().replace(".class", "");
        int generic = normalized.indexOf('<');
        return generic >= 0 ? normalized.substring(0, generic) : normalized;
    }

    private static String simpleName(String value) {
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private static AtlasDiagnostic diagnostic(String code, String message, ArchitectureAsset asset) {
        return new AtlasDiagnostic(
                AtlasId.of("diagnostic:scenario:" + encode(code + ":" + asset.id())),
                DiagnosticSeverity.WARNING,
                code,
                message,
                Optional.of(asset.evidence().getFirst().locator()));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 仅使用 Package 内由源码证明的 {@link RelationKind#IMPLEMENTS} 边计算可赋值性。
     * 没有继承关系证据时退化为名称匹配，不能依赖反射、外部 classpath 或猜测实现类。
     */
    private static final class TypeHierarchy {

        private final Map<String, List<String>> directSupertypes;
        private final Map<String, List<String>> directSupertypesBySimpleName;

        private TypeHierarchy(Map<String, List<String>> directSupertypes) {
            this.directSupertypes = Map.copyOf(directSupertypes);
            Map<String, List<String>> bySimpleName = new LinkedHashMap<>();
            this.directSupertypes.forEach((subtype, supertypes) -> bySimpleName
                    .computeIfAbsent(simpleName(subtype), ignored -> new ArrayList<>()).addAll(supertypes));
            bySimpleName.replaceAll((ignored, supertypes) -> supertypes.stream().distinct().sorted().toList());
            this.directSupertypesBySimpleName = Map.copyOf(bySimpleName);
        }

        static TypeHierarchy from(AtlasPackage atlasPackage) {
            Map<AtlasId, CodeElement> code = atlasPackage.designAtlas().nodes().stream()
                    .filter(CodeElement.class::isInstance).map(CodeElement.class::cast)
                    .collect(java.util.stream.Collectors.toMap(CodeElement::id, value -> value, (first, ignored) -> first,
                            LinkedHashMap::new));
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (AtlasRelation relation : atlasPackage.designAtlas().relations()) {
                if (relation.kind() != RelationKind.IMPLEMENTS) {
                    continue;
                }
                CodeElement subtype = code.get(relation.sourceId());
                CodeElement supertype = code.get(relation.targetId());
                if (subtype == null || supertype == null) {
                    continue;
                }
                result.computeIfAbsent(normalize(subtype.qualifiedName()), ignored -> new ArrayList<>())
                        .add(normalize(supertype.qualifiedName()));
            }
            result.replaceAll((ignored, types) -> types.stream().filter(type -> !type.isBlank()).distinct().sorted().toList());
            return new TypeHierarchy(result);
        }

        boolean compatible(String requestedType, ComponentDefinition component) {
            String requested = normalize(requestedType);
            return requested.equals(normalize(component.beanName())) || component.qualifiers().stream()
                    .map(DefaultConfigurationScenarioResolver::normalize).anyMatch(requested::equals)
                    || assignableTo(component.declaredType(), requested, new LinkedHashSet<>())
                    || assignableTo(component.candidateType(), requested, new LinkedHashSet<>());
        }

        private boolean assignableTo(String candidateType, String requestedType, Set<String> visited) {
            String candidate = normalize(candidateType);
            if (sameType(candidate, requestedType)) {
                return true;
            }
            if (!visited.add(candidate)) {
                return false;
            }
            // 每一次可赋值判断都会出现在“注入点 × Bean 候选”组合中。这里必须按类型直接命中；不能为每次
            // 递归都扫描全项目继承图，否则大型项目的场景查询会退化为数十亿次字符串比较。
            List<String> supertypes = directSupertypes.get(candidate);
            if (supertypes == null && !candidate.contains(".")) {
                supertypes = directSupertypesBySimpleName.get(candidate);
            }
            return (supertypes == null ? List.<String>of() : supertypes).stream()
                    .anyMatch(supertype -> assignableTo(supertype, requestedType, visited));
        }
    }

    private record ComponentEvaluation(ConditionStatus status, String reason) {
        private ComponentEvaluation {
            Objects.requireNonNull(status, "status 不能为空");
            reason = reason == null ? "" : reason.trim();
        }
    }

    /** 解析 Spring Profile 的 {@code !}/{@code &}/{@code |}/{@code ()} 布尔表达式；语法不完整时调用方必须保留未决。 */
    private static final class ProfileExpression {

        private final String expression;
        private final List<String> activeProfiles;
        private int cursor;

        private ProfileExpression(String expression, List<String> activeProfiles) {
            this.expression = Objects.requireNonNull(expression, "profile expression 不能为空").trim();
            this.activeProfiles = List.copyOf(Objects.requireNonNull(activeProfiles, "activeProfiles 不能为空"));
            if (this.expression.isEmpty()) {
                throw new IllegalArgumentException("Profile 表达式不能为空");
            }
        }

        private boolean matches() {
            boolean result = parseOr();
            skipWhitespace();
            if (cursor != expression.length()) {
                throw new IllegalArgumentException("Profile 表达式存在无法解析的尾部");
            }
            return result;
        }

        private boolean parseOr() {
            boolean result = parseAnd();
            while (consume('|')) {
                result |= parseAnd();
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parseUnary();
            while (consume('&')) {
                result &= parseUnary();
            }
            return result;
        }

        private boolean parseUnary() {
            if (consume('!')) {
                return !parseUnary();
            }
            if (consume('(')) {
                boolean result = parseOr();
                if (!consume(')')) {
                    throw new IllegalArgumentException("Profile 表达式缺少右括号");
                }
                return result;
            }
            String profile = token();
            return activeProfiles.contains(profile);
        }

        private String token() {
            skipWhitespace();
            int start = cursor;
            while (cursor < expression.length()) {
                char character = expression.charAt(cursor);
                if (Character.isWhitespace(character) || character == '!' || character == '&' || character == '|'
                        || character == '(' || character == ')') {
                    break;
                }
                cursor++;
            }
            if (start == cursor) {
                throw new IllegalArgumentException("Profile 表达式缺少 profile 名称");
            }
            return expression.substring(start, cursor);
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (cursor < expression.length() && expression.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) {
                cursor++;
            }
        }
    }
}
