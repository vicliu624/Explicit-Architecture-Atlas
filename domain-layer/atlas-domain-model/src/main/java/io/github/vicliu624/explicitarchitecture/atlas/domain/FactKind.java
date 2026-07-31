package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 技术分析器可以独立证明的原始事实类型。 */
public enum FactKind {
    PROJECT,
    MAVEN_MODULE,
    MAVEN_DEPENDENCY,
    SOURCE_TYPE,
    SOURCE_FIELD,
    SOURCE_METHOD,
    ANNOTATION,
    VALIDATION_RULE,
    METHOD_CALL,
    EVENT_PUBLICATION,
    EVENT_CONSUMPTION,
    PROJECTION_WRITE,
    PROCESS_START,
    PROCESS_WAIT,
    PROCESS_DISPATCH,
    PROCESS_COMPLETION,
    RUNTIME_MEASUREMENT
}
