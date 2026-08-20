package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 组件实例生命周期；Definition 和实际 Instance 不能混同。 */
public enum ComponentScope {
    SINGLETON,
    PROTOTYPE,
    REQUEST,
    SESSION,
    APPLICATION,
    THREAD,
    REFRESH,
    CUSTOM,
    UNKNOWN
}
