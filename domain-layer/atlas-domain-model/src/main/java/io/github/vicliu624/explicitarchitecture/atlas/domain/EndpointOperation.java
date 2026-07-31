package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 一个面向外部调用者的业务操作，不绑定具体 HTTP 框架。 */
public record EndpointOperation(AssetMetadata metadata, String purpose) implements ArchitectureAsset {

    public EndpointOperation {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(purpose, "purpose 不能为空");
        purpose = purpose.trim();
        if (purpose.isEmpty()) {
            throw new IllegalArgumentException("purpose 不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.ENDPOINT_OPERATION;
    }
}
