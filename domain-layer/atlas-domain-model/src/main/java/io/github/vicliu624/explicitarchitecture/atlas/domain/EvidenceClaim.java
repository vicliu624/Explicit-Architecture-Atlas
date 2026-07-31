package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 证据表达的是声明、推导、运行观察还是尚未实现的计划。 */
public enum EvidenceClaim {
    DECLARED,
    DERIVED,
    OBSERVED,
    PLANNED
}
