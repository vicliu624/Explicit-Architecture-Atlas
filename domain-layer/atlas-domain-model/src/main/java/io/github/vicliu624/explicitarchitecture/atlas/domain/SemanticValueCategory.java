package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 值在实现链路中的业务语义类别；同一 Java 类型可以对应多个类别。 */
public enum SemanticValueCategory {
    RAW_INPUT,
    DOMAIN_PRIMITIVE,
    VALUE_OBJECT,
    ENTITY_IDENTIFIER,
    AGGREGATE_STATE,
    COMMAND_PAYLOAD,
    EVENT_PAYLOAD,
    QUERY_FILTER,
    READ_MODEL_VALUE,
    PERSISTED_REPRESENTATION,
    EXTERNAL_REQUEST,
    EXTERNAL_RESPONSE,
    CONFIGURATION_VALUE,
    SECURITY_CONTEXT,
    TENANT_CONTEXT,
    TIME_SOURCE,
    ERROR_DETAIL,
    AUDIT_VALUE,
    SECRET,
    CUSTOM
}
