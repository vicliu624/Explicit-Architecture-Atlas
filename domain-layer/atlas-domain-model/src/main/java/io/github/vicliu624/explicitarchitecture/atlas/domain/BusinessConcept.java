package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 面向业务人员呈现的稳定领域概念。 */
public record BusinessConcept(AssetMetadata metadata, String responsibility) implements ArchitectureAsset {

    public BusinessConcept {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(responsibility, "responsibility 不能为空");
        responsibility = responsibility.trim();
        if (responsibility.isEmpty()) {
            throw new IllegalArgumentException("responsibility 不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.BUSINESS_CONCEPT;
    }
}
