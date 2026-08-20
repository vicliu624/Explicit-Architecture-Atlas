package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 操作参数或隐式输入的类型化摘要。 */
public record OperationInput(
        String name,
        String javaType,
        OperationInputRole role,
        boolean implicit,
        ValueNullability nullability,
        String description) {

    public OperationInput {
        name = text(name, "name");
        javaType = text(javaType, "javaType");
        Objects.requireNonNull(role, "role 不能为空");
        Objects.requireNonNull(nullability, "nullability 不能为空");
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
