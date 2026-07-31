package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.List;
import java.util.Objects;

/** 专家模式用于解释关系及其证据的稳定图边视图。 */
public record AtlasRelationView(
        String id,
        String kind,
        AtlasAssetReference source,
        AtlasAssetReference target,
        List<AtlasProperty> attributes,
        List<AtlasEvidenceView> evidence) {

    public AtlasRelationView {
        Objects.requireNonNull(id, "id 不能为空");
        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id 不能为空白");
        }
        Objects.requireNonNull(kind, "kind 不能为空");
        kind = kind.trim();
        if (kind.isEmpty()) {
            throw new IllegalArgumentException("kind 不能为空白");
        }
        Objects.requireNonNull(source, "source 不能为空");
        Objects.requireNonNull(target, "target 不能为空");
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
    }
}
