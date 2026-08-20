package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 注入点请求单一、可选、多个还是延迟解析组件。 */
public enum InjectionCardinality {
    SINGLE,
    OPTIONAL,
    COLLECTION,
    MAP,
    PROVIDER,
    UNKNOWN
}
