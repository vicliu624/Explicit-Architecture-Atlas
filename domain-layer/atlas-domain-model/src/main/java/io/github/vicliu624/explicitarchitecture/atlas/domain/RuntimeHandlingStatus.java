package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 一条运行态处理记录明确报告的结果；未知状态只能由 Overlay 编译器在缺少观测时生成。 */
public enum RuntimeHandlingStatus {
    SUCCEEDED,
    FAILED,
    LAGGING,
    RETRYING
}
