package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 某个设计资产或关系在一个时间点的运行态观测。 */
public record RuntimeObservation(
        AtlasId id,
        AtlasId subjectId,
        Instant observedAt,
        String observationType,
        Map<String, AtlasValue> attributes,
        Evidence evidence) {

    public RuntimeObservation {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(subjectId, "subjectId 不能为空");
        Objects.requireNonNull(observedAt, "observedAt 不能为空");
        Objects.requireNonNull(observationType, "observationType 不能为空");
        observationType = observationType.trim();
        if (observationType.isEmpty()) {
            throw new IllegalArgumentException("observationType 不能为空白");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        Objects.requireNonNull(evidence, "evidence 不能为空");
        if (evidence.claim() != EvidenceClaim.OBSERVED) {
            throw new IllegalArgumentException("运行态观测证据必须标记为 OBSERVED");
        }
    }
}
