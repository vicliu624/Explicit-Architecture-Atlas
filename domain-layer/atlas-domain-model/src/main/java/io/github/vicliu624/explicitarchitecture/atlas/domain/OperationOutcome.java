package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 操作的返回值或可观察副作用摘要。 */
public record OperationOutcome(
        OperationOutcomeKind kind,
        String name,
        String javaType,
        String condition,
        String description) {

    public OperationOutcome {
        Objects.requireNonNull(kind, "kind 不能为空");
        name = text(name, "name");
        javaType = text(javaType, "javaType");
        condition = condition == null ? "" : condition.trim();
        description = description == null ? "" : description.trim();
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
