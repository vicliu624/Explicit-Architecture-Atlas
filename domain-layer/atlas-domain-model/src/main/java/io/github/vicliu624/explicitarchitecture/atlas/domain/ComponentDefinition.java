package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/** 一个 DI 容器可选择激活的组件定义，而非某次运行中的代理实例。 */
public record ComponentDefinition(
        AssetMetadata metadata,
        ComponentKind componentKind,
        String beanName,
        String declaredType,
        String candidateType,
        ComponentScope scope,
        boolean primary,
        List<String> qualifiers,
        ConditionExpression activationCondition) implements ArchitectureAsset {

    public ComponentDefinition {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(componentKind, "componentKind 不能为空");
        beanName = text(beanName, "beanName");
        declaredType = text(declaredType, "declaredType");
        candidateType = text(candidateType, "candidateType");
        Objects.requireNonNull(scope, "scope 不能为空");
        qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers 不能为空").stream()
                .map(value -> text(value, "qualifier")).distinct().sorted().toList());
        Objects.requireNonNull(activationCondition, "activationCondition 不能为空");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.COMPONENT_DEFINITION;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
