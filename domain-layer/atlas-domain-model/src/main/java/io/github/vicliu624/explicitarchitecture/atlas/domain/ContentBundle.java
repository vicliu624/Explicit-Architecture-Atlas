package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 与设计事实分离管理的人类可读说明集合。 */
public record ContentBundle(List<ContentEntry> entries) {

    public ContentBundle {
        Objects.requireNonNull(entries, "entries 不能为空");
        Map<AtlasId, ContentEntry> byId = new LinkedHashMap<>();
        for (ContentEntry entry : entries) {
            entry = Objects.requireNonNull(entry, "内容条目不能为空");
            if (byId.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalArgumentException("内容条目身份重复: " + entry.id());
            }
        }
        entries = List.copyOf(byId.values().stream().sorted(Comparator.comparing(ContentEntry::id)).toList());
    }

    public static ContentBundle empty() {
        return new ContentBundle(List.of());
    }
}
