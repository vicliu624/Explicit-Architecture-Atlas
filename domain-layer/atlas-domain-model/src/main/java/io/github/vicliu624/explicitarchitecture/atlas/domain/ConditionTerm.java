package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 组成规范化条件表达式的一个可解释原子项。 */
public record ConditionTerm(String kind, String subject, String operator, String expectedValue) {

    public ConditionTerm {
        kind = text(kind, "kind");
        subject = text(subject, "subject");
        operator = text(operator, "operator");
        expectedValue = expectedValue == null ? "" : expectedValue.trim();
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
