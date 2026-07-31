package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 投影或集成最终写入的数据目标类别。 */
public enum DataTargetKind {
    /** JPA 等 ORM 已确认的持久化实体；未映射显式物理表时不臆造表名。 */
    PERSISTENCE_ENTITY,
    DATABASE_TABLE,
    DATABASE_COLUMN,
    DATABASE_VIEW,
    CACHE,
    SEARCH_INDEX,
    MESSAGE_TOPIC,
    EXTERNAL_SYSTEM,
    FILE,
    UNSPECIFIED
}
