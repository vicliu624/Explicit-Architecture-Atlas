package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 语义值的容器/基数语义，不能只通过擦除后的 Java 类型表达。 */
public enum ValueCardinality {
    SINGLE,
    OPTIONAL,
    COLLECTION,
    MAP,
    STREAM,
    ASYNC_SINGLE,
    ASYNC_STREAM,
    UNKNOWN
}
