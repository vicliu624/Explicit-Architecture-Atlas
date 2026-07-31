package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 原始事实之间的关系候选；归一化后可成为 {@link AtlasRelation}。 */
public record FactRelation(
        AtlasId id,
        RelationKind kind,
        AtlasId sourceFactId,
        AtlasId targetFactId,
        Map<String, AtlasValue> attributes,
        List<Evidence> evidence) {

    public FactRelation {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(sourceFactId, "sourceFactId 不能为空");
        Objects.requireNonNull(targetFactId, "targetFactId 不能为空");
        if (sourceFactId.equals(targetFactId)) {
            throw new IllegalArgumentException("事实关系不能指向自身");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("事实关系至少需要一项证据");
        }
    }
}
