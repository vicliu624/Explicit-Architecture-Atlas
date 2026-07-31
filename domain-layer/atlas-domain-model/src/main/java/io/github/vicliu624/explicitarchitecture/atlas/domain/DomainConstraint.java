package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/** 可由业务人员阅读、由开发者回溯源码的领域约束。 */
public record DomainConstraint(
        String id,
        String subjectId,
        DomainConstraintKind kind,
        EnforcementLevel enforcementLevel,
        String statement,
        List<Evidence> evidence
) {
    public DomainConstraint {
        if (id == null || id.isBlank() || subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("约束 id 与 subjectId 不能为空");
        }
        Objects.requireNonNull(kind, "约束 kind 不能为空");
        Objects.requireNonNull(enforcementLevel, "执行级别不能为空");
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("约束说明不能为空");
        }
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
