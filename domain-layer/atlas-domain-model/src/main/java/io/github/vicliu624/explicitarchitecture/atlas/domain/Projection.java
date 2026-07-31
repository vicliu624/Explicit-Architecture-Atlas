package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 为某个角色或查询目的维护的读模型投影。 */
public record Projection(AssetMetadata metadata, String purpose) implements ArchitectureAsset {

    public Projection {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(purpose, "purpose 不能为空");
        purpose = purpose.trim();
        if (purpose.isEmpty()) {
            throw new IllegalArgumentException("purpose 不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.PROJECTION;
    }
}
