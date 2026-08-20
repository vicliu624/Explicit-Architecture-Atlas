package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/**
 * 具备业务身份的值，而不是仅按 Java 类型聚合的参数或字段。
 *
 * <p>规则、归一化、HTTP/事件/数据库表示和具体出现位置通过图关系连接到该节点；这样一个 Java {@code String}
 * 可以分别表示原始输入、领域原语底层值、外部编号或持久化字段。</p>
 */
public record SemanticValue(
        AssetMetadata metadata,
        SemanticValueCategory category,
        String javaRepresentation,
        ValueCardinality cardinality,
        ValueNullability nullability,
        TrustLevel trustLevel,
        DataSensitivity sensitivity,
        String lifecycleState) implements ArchitectureAsset {

    public SemanticValue {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(category, "category 不能为空");
        javaRepresentation = requireText(javaRepresentation, "javaRepresentation");
        Objects.requireNonNull(cardinality, "cardinality 不能为空");
        Objects.requireNonNull(nullability, "nullability 不能为空");
        Objects.requireNonNull(trustLevel, "trustLevel 不能为空");
        Objects.requireNonNull(sensitivity, "sensitivity 不能为空");
        lifecycleState = requireText(lifecycleState, "lifecycleState");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.SEMANTIC_VALUE;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
