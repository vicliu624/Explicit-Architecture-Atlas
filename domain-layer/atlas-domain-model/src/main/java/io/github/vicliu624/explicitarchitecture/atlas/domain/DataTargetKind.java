package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 投影或集成最终写入的数据目标类别。 */
public enum DataTargetKind {
    DATABASE_TABLE,
    DATABASE_VIEW,
    CACHE,
    SEARCH_INDEX,
    MESSAGE_TOPIC,
    EXTERNAL_SYSTEM,
    FILE,
    UNSPECIFIED
}
