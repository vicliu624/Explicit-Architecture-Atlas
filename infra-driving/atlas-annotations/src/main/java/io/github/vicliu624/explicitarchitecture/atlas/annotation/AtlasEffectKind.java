package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/** 操作的可观察副作用类别。 */
public enum AtlasEffectKind {
    STATE_MUTATION,
    EVENT_PUBLICATION,
    PERSISTENCE_WRITE,
    CACHE_WRITE,
    EXTERNAL_CALL,
    ASYNC_CONTINUATION,
    AUDIT_RECORD,
    CUSTOM
}
