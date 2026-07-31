package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 将外部操作翻译为领域行为的应用用例。 */
public record ApplicationUseCase(AssetMetadata metadata, String purpose) implements ArchitectureAsset {

    public ApplicationUseCase {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(purpose, "purpose 不能为空");
        purpose = purpose.trim();
        if (purpose.isEmpty()) {
            throw new IllegalArgumentException("purpose 不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.APPLICATION_USE_CASE;
    }
}
