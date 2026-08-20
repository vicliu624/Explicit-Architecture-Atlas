package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/** 组件请求其他组件的一个可场景化解析的注入点。 */
public record InjectionPoint(
        AssetMetadata metadata,
        AtlasId ownerComponentId,
        String requestedType,
        InjectionKind injectionKind,
        InjectionCardinality cardinality,
        boolean required,
        List<String> qualifiers) implements ArchitectureAsset {

    public InjectionPoint {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(ownerComponentId, "ownerComponentId 不能为空");
        Objects.requireNonNull(requestedType, "requestedType 不能为空");
        requestedType = requestedType.trim();
        if (requestedType.isEmpty()) {
            throw new IllegalArgumentException("requestedType 不能为空白");
        }
        Objects.requireNonNull(injectionKind, "injectionKind 不能为空");
        Objects.requireNonNull(cardinality, "cardinality 不能为空");
        qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers 不能为空").stream()
                .map(value -> Objects.requireNonNull(value, "qualifier 不能为空").trim())
                .filter(value -> !value.isEmpty()).distinct().sorted().toList());
    }

    @Override
    public AssetKind kind() {
        return AssetKind.INJECTION_POINT;
    }
}
