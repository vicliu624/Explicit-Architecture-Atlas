package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 静态调用边在一个配置场景中的有效分派说明。
 *
 * <p>调用方和静态目标均为 CodeElement 身份；候选/选择的组件与实际实现方法分开保存，避免将接口声明、Bean
 * 选择和 Java 虚分派压缩成一条不透明的 {@code CALLS} 边。Advisor 仅表示源码已声明的拦截候选，真实代理链仍可由
 * Runtime Overlay 覆盖或证实。</p>
 */
public record ScenarioInvocationResolution(
        AtlasId callerCodeId,
        AtlasId staticTargetCodeId,
        ScenarioInvocationStatus status,
        List<AtlasId> receiverInjectionPointIds,
        List<AtlasId> candidateComponents,
        List<AtlasId> selectedComponents,
        List<AtlasId> effectiveTargetCodeIds,
        List<AtlasId> declaredAdvisorIds,
        List<String> reasons) {

    public ScenarioInvocationResolution {
        Objects.requireNonNull(callerCodeId, "callerCodeId 不能为空");
        Objects.requireNonNull(staticTargetCodeId, "staticTargetCodeId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        receiverInjectionPointIds = ids(receiverInjectionPointIds, "receiverInjectionPointIds");
        candidateComponents = ids(candidateComponents, "candidateComponents");
        selectedComponents = ids(selectedComponents, "selectedComponents");
        effectiveTargetCodeIds = ids(effectiveTargetCodeIds, "effectiveTargetCodeIds");
        declaredAdvisorIds = ids(declaredAdvisorIds, "declaredAdvisorIds");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons 不能为空").stream()
                .map(reason -> Objects.requireNonNull(reason, "reason 不能为空").trim())
                .filter(reason -> !reason.isEmpty()).distinct().sorted().toList());
    }

    private static List<AtlasId> ids(List<AtlasId> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空").stream()
                .map(value -> Objects.requireNonNull(value, field + " 不能包含 null"))
                .distinct().sorted(Comparator.naturalOrder()).toList());
    }
}
