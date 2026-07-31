package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 可由业务人员阅读、由开发者回溯源码的领域约束。 */
public record DomainConstraint(
        AssetMetadata metadata,
        AtlasId subjectId,
        DomainConstraintKind constraintKind,
        EnforcementLevel enforcementLevel,
        String statement
) implements ArchitectureAsset {
    public DomainConstraint {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(subjectId, "subjectId 不能为空");
        Objects.requireNonNull(constraintKind, "constraintKind 不能为空");
        Objects.requireNonNull(enforcementLevel, "enforcementLevel 不能为空");
        Objects.requireNonNull(statement, "statement 不能为空");
        statement = statement.trim();
        if (statement.isEmpty()) {
            throw new IllegalArgumentException("约束说明不能为空白");
        }
    }

    @Override
    public AssetKind kind() {
        return AssetKind.DOMAIN_CONSTRAINT;
    }
}
