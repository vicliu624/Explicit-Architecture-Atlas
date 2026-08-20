package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 可影响 Bean 选择、函数分支或外部行为的配置键；默认不保存敏感运行时值。 */
public record ConfigurationProperty(
        AssetMetadata metadata,
        String propertyKey,
        String declaredDefault,
        DataSensitivity sensitivity) implements ArchitectureAsset {

    public ConfigurationProperty {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(propertyKey, "propertyKey 不能为空");
        propertyKey = propertyKey.trim();
        if (propertyKey.isEmpty()) {
            throw new IllegalArgumentException("propertyKey 不能为空白");
        }
        declaredDefault = declaredDefault == null ? "" : declaredDefault.trim();
        Objects.requireNonNull(sensitivity, "sensitivity 不能为空");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.CONFIGURATION_PROPERTY;
    }
}
