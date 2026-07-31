package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 系统边界另一侧的交互对象类别。 */
public enum BoundaryKind {
    REST_API,
    MESSAGE_BROKER,
    REMOTE_SERVICE,
    IDENTITY_PROVIDER,
    FILE_EXCHANGE,
    HUMAN_OPERATOR,
    UNSPECIFIED
}
