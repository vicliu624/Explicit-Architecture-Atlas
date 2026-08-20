package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 操作失败的可解释结果；异常不会因为未出现在 Java return 中而丢失。 */
public record OperationFailure(String exceptionType, String condition, String description) {

    public OperationFailure {
        Objects.requireNonNull(exceptionType, "exceptionType 不能为空");
        exceptionType = exceptionType.trim();
        if (exceptionType.isEmpty()) {
            throw new IllegalArgumentException("exceptionType 不能为空白");
        }
        condition = condition == null ? "" : condition.trim();
        description = description == null ? "" : description.trim();
    }
}
