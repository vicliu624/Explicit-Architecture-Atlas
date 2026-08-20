package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 由 IoC/DI 容器管理的组件定义来源。 */
public enum ComponentKind {
    COMPONENT,
    SERVICE,
    REPOSITORY,
    CONTROLLER,
    CONFIGURATION,
    FACTORY_METHOD,
    FACTORY_BEAN,
    AUTO_CONFIGURATION,
    XML_DEFINITION,
    PROGRAMMATIC,
    TEST_CONFIGURATION,
    CUSTOM
}
