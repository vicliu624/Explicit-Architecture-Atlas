package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;

import java.util.List;
import java.util.Objects;

/** 一次可复现分析产生的 Atlas 数据快照。 */
public record AtlasSnapshot(String id, List<ArchitectureAsset> assets) {

    public AtlasSnapshot {
        Objects.requireNonNull(id, "id 不能为空");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空白");
        }
        assets = List.copyOf(Objects.requireNonNull(assets, "assets 不能为空"));
    }

    public static AtlasSnapshot emptyFor(String projectId) {
        return new AtlasSnapshot(projectId + "-empty", List.of());
    }
}
