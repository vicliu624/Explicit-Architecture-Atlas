package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 领域事件对外携带的一个字段。 */
public record EventField(String name, EventFieldRole role, String businessMeaning) {

    public EventField {
        Objects.requireNonNull(name, "name 不能为空");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        Objects.requireNonNull(role, "role 不能为空");
        businessMeaning = businessMeaning == null ? "" : businessMeaning.trim();
    }
}
