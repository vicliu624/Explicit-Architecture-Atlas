package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 源码、归档或配置中可供专家展开查看的元素。 */
public record CodeElement(
        AssetMetadata metadata,
        CodeElementKind elementKind,
        String qualifiedName,
        SourceLocator locator) implements ArchitectureAsset {

    public CodeElement {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(elementKind, "elementKind 不能为空");
        Objects.requireNonNull(qualifiedName, "qualifiedName 不能为空");
        qualifiedName = qualifiedName.trim();
        if (qualifiedName.isEmpty()) {
            throw new IllegalArgumentException("qualifiedName 不能为空白");
        }
        Objects.requireNonNull(locator, "locator 不能为空");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.CODE_ELEMENT;
    }
}
