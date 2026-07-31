package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 某个投影或 Process Manager 在一次 Overlay 编译后的可直接渲染健康摘要。 */
public record RuntimeAssetHealth(
        AtlasId subjectId,
        RuntimeHealthStatus status,
        Instant assessedAt,
        Optional<RuntimeObservation> latestObservation,
        List<AtlasId> impactedDataTargetIds,
        String operationalSummary,
        String suggestedAction) {

    public RuntimeAssetHealth {
        Objects.requireNonNull(subjectId, "subjectId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(assessedAt, "assessedAt 不能为空");
        latestObservation = Objects.requireNonNull(latestObservation, "latestObservation 不能为空");
        impactedDataTargetIds = List.copyOf(Objects.requireNonNull(impactedDataTargetIds, "impactedDataTargetIds 不能为空").stream()
                .map(id -> Objects.requireNonNull(id, "受影响数据目标不能为空"))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList());
        operationalSummary = requiredText(operationalSummary, "operationalSummary");
        suggestedAction = suggestedAction == null ? "" : suggestedAction.trim();
    }

    private static String requiredText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value;
    }
}
