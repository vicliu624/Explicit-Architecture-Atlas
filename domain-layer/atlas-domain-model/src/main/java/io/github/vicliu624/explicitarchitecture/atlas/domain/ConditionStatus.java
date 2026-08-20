package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 条件在没有或已经指定场景时的解析状态。 */
public enum ConditionStatus {
    POSSIBLE,
    ACTIVE,
    EXCLUDED,
    UNRESOLVED,
    OBSERVED
}
