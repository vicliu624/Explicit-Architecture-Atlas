package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/** guard 成功后对值流或控制流产生的结果。 */
public enum AtlasGuardSuccess {
    VOID,
    PASS_THROUGH_SUBJECT,
    PROVES_PREDICATE,
    PROVES_NEGATED_PREDICATE,
    NORMALIZES_SUBJECT,
    CUSTOM
}
