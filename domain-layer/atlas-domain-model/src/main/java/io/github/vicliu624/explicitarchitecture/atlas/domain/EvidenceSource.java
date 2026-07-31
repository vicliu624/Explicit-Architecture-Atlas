package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 结论来自何处；意图、代码事实与运行事实必须明确分离。 */
public enum EvidenceSource {
    DECLARED,
    DERIVED,
    RUNTIME_OBSERVED,
    PLANNED
}
