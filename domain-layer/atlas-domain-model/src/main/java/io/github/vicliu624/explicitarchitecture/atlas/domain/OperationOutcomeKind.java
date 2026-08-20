package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 操作的直接或副作用结果。 */
public enum OperationOutcomeKind {
    RETURN_VALUE,
    STATE_MUTATION,
    EVENT_PUBLICATION,
    PERSISTENCE_WRITE,
    CACHE_WRITE,
    EXTERNAL_CALL,
    ASYNC_CONTINUATION,
    AUDIT_RECORD,
    CUSTOM
}
