package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 值进入某操作时的可信程度。 */
public enum TrustLevel {
    EXTERNAL_UNTRUSTED,
    INTERNAL,
    VERIFIED,
    SYSTEM_GENERATED,
    UNKNOWN
}
