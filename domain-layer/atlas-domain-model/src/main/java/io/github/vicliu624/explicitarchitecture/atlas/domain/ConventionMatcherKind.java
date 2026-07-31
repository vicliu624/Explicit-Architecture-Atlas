package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 可由源码分析器稳定识别的项目约定匹配器。 */
public enum ConventionMatcherKind {
    ANNOTATION,
    TYPE_ASSIGNABLE_TO,
    METHOD_CALL,
    PACKAGE_PREFIX
}
