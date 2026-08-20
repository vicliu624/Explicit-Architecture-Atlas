package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 一个注入点在指定场景中的候选、选择结果和未决原因。 */
public record ScenarioInjectionResolution(
        AtlasId injectionPointId,
        ConditionStatus status,
        List<AtlasId> candidates,
        List<AtlasId> selected,
        List<String> reasons) {

    public ScenarioInjectionResolution {
        Objects.requireNonNull(injectionPointId, "injectionPointId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        candidates = sortIds(candidates, "candidates");
        selected = sortIds(selected, "selected");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons 不能为空").stream()
                .map(value -> Objects.requireNonNull(value, "reason 不能为空").trim())
                .filter(value -> !value.isEmpty()).distinct().sorted().toList());
    }

    private static List<AtlasId> sortIds(List<AtlasId> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空").stream()
                .map(value -> Objects.requireNonNull(value, field + " 不能包含 null")).distinct()
                .sorted(Comparator.naturalOrder()).toList());
    }
}
