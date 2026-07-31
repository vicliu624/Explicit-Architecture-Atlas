package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/**
 * Atlas 范围外的系统、人或交互通道。
 *
 * <p>它让调用链、事件通道和投影目的地能够明确停在真实边界处，而非虚构成当前工程内的实现节点。</p>
 */
public record ExternalBoundary(
        AssetMetadata metadata,
        BoundaryKind boundaryKind,
        String responsibility) implements ArchitectureAsset {

    public ExternalBoundary {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(boundaryKind, "boundaryKind 不能为空");
        Objects.requireNonNull(responsibility, "responsibility 不能为空");
        responsibility = responsibility.trim();
        if (responsibility.isEmpty()) {
            throw new IllegalArgumentException("responsibility 不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.EXTERNAL_BOUNDARY;
    }
}
