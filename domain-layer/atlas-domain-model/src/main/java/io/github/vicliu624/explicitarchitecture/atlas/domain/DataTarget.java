package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 投影或集成写入的最终可见性/持久化目标。 */
public record DataTarget(AssetMetadata metadata, DataTargetKind targetKind) implements ArchitectureAsset {

    public DataTarget {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(targetKind, "targetKind 不能为空");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.DATA_TARGET;
    }
}
