package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/**
 * Atlas 图谱中的稳定身份。
 *
 * <p>身份由显式架构注解、约定 Profile 或可复现的事实解析生成；它不能依赖 Java 简单类名或本机绝对路径。</p>
 */
public record AtlasId(String value) implements Comparable<AtlasId> {

    public AtlasId {
        Objects.requireNonNull(value, "value 不能为空");
        value = value.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Atlas 身份不能为空或包含空白字符");
        }
    }

    public static AtlasId of(String value) {
        return new AtlasId(value);
    }

    @Override
    public int compareTo(AtlasId other) {
        return value.compareTo(Objects.requireNonNull(other, "other 不能为空").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
