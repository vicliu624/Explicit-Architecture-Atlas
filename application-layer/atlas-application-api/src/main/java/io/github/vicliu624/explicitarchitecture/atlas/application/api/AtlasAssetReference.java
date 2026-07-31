package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 可跨角色复用的资产卡片引用；不携带源码正文或原始解析事实。 */
public record AtlasAssetReference(String id, String name, String kind, String summary) {

    public AtlasAssetReference {
        id = required(id, "id");
        name = required(name, "name");
        kind = required(kind, "kind");
        summary = summary == null ? "" : summary.trim();
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空白");
        }
        return value;
    }
}
