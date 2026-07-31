package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 所有 Atlas 节点共享的稳定元数据。 */
public record AssetMetadata(
        AtlasId id,
        String name,
        Map<String, AtlasValue> attributes,
        List<Evidence> evidence) {

    public AssetMetadata {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(name, "name 不能为空");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("Atlas 节点至少需要一项证据");
        }
    }

    public static AssetMetadata of(AtlasId id, String name, List<Evidence> evidence) {
        return new AssetMetadata(id, name, Map.of(), evidence);
    }
}
