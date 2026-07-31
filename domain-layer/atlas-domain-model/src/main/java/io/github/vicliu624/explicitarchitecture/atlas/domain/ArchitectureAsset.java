package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/** Atlas 图谱中的通用资产；每项资产至少应有一项证据。 */
public record ArchitectureAsset(String id, AssetKind kind, String name, List<Evidence> evidence) {
    public ArchitectureAsset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("资产 id 不能为空");
        }
        Objects.requireNonNull(kind, "资产 kind 不能为空");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("资产 name 不能为空");
        }
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("架构资产至少需要一项证据");
        }
    }
}
