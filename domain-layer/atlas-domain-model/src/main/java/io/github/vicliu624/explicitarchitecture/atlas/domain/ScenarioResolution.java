package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 从可能组件图解析出的一个场景化有效组件与注入结果。 */
public record ScenarioResolution(
        ConfigurationScenario scenario,
        List<ScenarioComponentStatus> components,
        List<ScenarioInjectionResolution> injections,
        List<ScenarioInvocationResolution> invocations,
        List<AtlasDiagnostic> diagnostics) {

    public ScenarioResolution {
        Objects.requireNonNull(scenario, "scenario 不能为空");
        components = List.copyOf(Objects.requireNonNull(components, "components 不能为空").stream()
                .sorted(Comparator.comparing(ScenarioComponentStatus::componentId)).toList());
        injections = List.copyOf(Objects.requireNonNull(injections, "injections 不能为空").stream()
                .sorted(Comparator.comparing(ScenarioInjectionResolution::injectionPointId)).toList());
        invocations = List.copyOf(Objects.requireNonNull(invocations, "invocations 不能为空").stream()
                .sorted(Comparator.comparing(ScenarioInvocationResolution::callerCodeId)
                        .thenComparing(ScenarioInvocationResolution::staticTargetCodeId)).toList());
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空").stream()
                .sorted(Comparator.comparing(AtlasDiagnostic::id)).toList());
    }

    /** 保持旧调用方的二进制前构造方式；旧调用方没有场景调用分派信息时只能得到空列表。 */
    public ScenarioResolution(
            ConfigurationScenario scenario,
            List<ScenarioComponentStatus> components,
            List<ScenarioInjectionResolution> injections,
            List<AtlasDiagnostic> diagnostics) {
        this(scenario, components, injections, List.of(), diagnostics);
    }
}
