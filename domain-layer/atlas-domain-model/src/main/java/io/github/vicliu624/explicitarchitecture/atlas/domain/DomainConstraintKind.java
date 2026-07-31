package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 领域规则的可视化分类。 */
public enum DomainConstraintKind {
    REQUIRED,
    IDENTITY_FORMAT,
    VALUE_RANGE,
    CROSS_CONCEPT_CONSISTENCY,
    UNIQUENESS,
    STATE_TRANSITION,
    EVENT_PRECONDITION,
    TEMPORAL,
    OWNERSHIP,
    COMPENSATION,
    CUSTOM
}
