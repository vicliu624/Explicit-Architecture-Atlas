package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/** 开发者可以在源码中声明的通用领域约束分类。 */
public enum ConstraintKind {
    REQUIRED,
    IDENTITY_FORMAT,
    NUMERIC_RANGE,
    CROSS_CONCEPT_CONSISTENCY,
    UNIQUENESS,
    STATE_TRANSITION,
    EVENT_PRECONDITION,
    TEMPORAL,
    OWNERSHIP,
    COMPENSATION,
    CUSTOM
}
