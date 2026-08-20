package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** DI 或延迟查找依赖的方式。 */
public enum InjectionKind {
    CONSTRUCTOR,
    FIELD,
    SETTER,
    METHOD_PARAMETER,
    LOOKUP_METHOD,
    OBJECT_PROVIDER,
    OBJECT_FACTORY,
    APPLICATION_CONTEXT_LOOKUP,
    BEAN_FACTORY_LOOKUP,
    PROGRAMMATIC_LOOKUP,
    CUSTOM
}
