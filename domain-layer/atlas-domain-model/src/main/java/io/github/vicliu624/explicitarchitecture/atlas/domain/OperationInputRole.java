package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 操作输入的角色；依赖和上下文不是普通业务参数。 */
public enum OperationInputRole {
    BUSINESS_INPUT,
    RAW_INPUT,
    DOMAIN_PRIMITIVE,
    COMMAND,
    QUERY,
    EVENT,
    DEPENDENCY,
    RECEIVER_STATE,
    CONFIGURATION,
    SECURITY_CONTEXT,
    TENANT_CONTEXT,
    TRANSACTION_CONTEXT,
    TIME_SOURCE,
    EXTERNAL_RESPONSE,
    PERSISTENCE_READ,
    CUSTOM
}
