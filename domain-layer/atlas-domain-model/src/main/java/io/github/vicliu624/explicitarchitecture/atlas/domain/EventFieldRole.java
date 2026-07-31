package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 事件字段在业务解释、关联和脱敏中的角色。 */
public enum EventFieldRole {
    BUSINESS_IDENTITY,
    BUSINESS_FACT,
    CORRELATION,
    TRACE,
    SENSITIVE,
    TECHNICAL,
    UNSPECIFIED
}
