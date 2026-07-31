package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 两个 Atlas 节点之间经过证据支撑的关系。 */
public record AtlasRelation(
        AtlasId id,
        RelationKind kind,
        AtlasId sourceId,
        AtlasId targetId,
        Map<String, AtlasValue> attributes,
        List<Evidence> evidence) {

    public AtlasRelation {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(sourceId, "sourceId 不能为空");
        Objects.requireNonNull(targetId, "targetId 不能为空");
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("关系的 sourceId 与 targetId 不能相同");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("Atlas 关系至少需要一项证据");
        }
    }
}
