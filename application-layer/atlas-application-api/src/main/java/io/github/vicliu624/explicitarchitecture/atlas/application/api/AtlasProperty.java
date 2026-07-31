package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 已归一为可读文本的属性；角色视图不把内部 AtlasValue 实现细节暴露给渲染器。 */
public record AtlasProperty(String name, String value) {

    public AtlasProperty {
        Objects.requireNonNull(name, "name 不能为空");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        value = value == null ? "" : value.trim();
    }
}
