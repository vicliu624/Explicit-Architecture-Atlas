package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Map;

/**
 * Atlas 图谱节点的公共根接口。
 *
 * <p>它只统一身份、名称、属性和证据；领域约束、事件、投影、流程和接口均以各自的类型表达，不能退化为一个
 * 带 {@code kind} 字段的万能 Map。</p>
 */
public interface ArchitectureAsset {

    AssetMetadata metadata();

    AssetKind kind();

    default AtlasId id() {
        return metadata().id();
    }

    default String name() {
        return metadata().name();
    }

    default Map<String, AtlasValue> attributes() {
        return metadata().attributes();
    }

    default List<Evidence> evidence() {
        return metadata().evidence();
    }
}
