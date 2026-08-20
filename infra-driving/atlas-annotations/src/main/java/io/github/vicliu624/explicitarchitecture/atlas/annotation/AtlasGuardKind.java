package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/** 项目自定义 guard/assert/require 函数的语义分类。 */
public enum AtlasGuardKind {
    NON_BLANK,
    REQUIRED,
    PREDICATE,
    PATTERN,
    PREFIX,
    RANGE,
    STATE,
    CUSTOM
}
