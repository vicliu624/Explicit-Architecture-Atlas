package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 值的可空约束来自类型、guard、控制流和文档的合并结论。 */
public enum ValueNullability {
    REQUIRED,
    OPTIONAL,
    CONDITIONAL,
    UNKNOWN
}
