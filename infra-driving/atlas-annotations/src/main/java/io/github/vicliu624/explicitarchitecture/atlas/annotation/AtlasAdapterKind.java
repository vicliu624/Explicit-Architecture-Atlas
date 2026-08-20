package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/** 项目私有基础设施抽象在 Atlas 中的通用角色。 */
public enum AtlasAdapterKind {
    COMMAND_DISPATCHER,
    EVENT_PUBLISHER,
    EVENT_CONSUMER,
    PROJECTION_WRITER,
    REPOSITORY,
    EXTERNAL_CLIENT,
    AUTHORIZATION_POLICY,
    CONFIGURATION_PROVIDER,
    CUSTOM
}
