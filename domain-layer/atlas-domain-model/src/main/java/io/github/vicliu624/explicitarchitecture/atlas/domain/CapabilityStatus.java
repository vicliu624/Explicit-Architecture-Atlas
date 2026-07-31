package io.github.vicliu624.explicitarchitecture.atlas.domain;

/**
 * 分析能力对指定范围的覆盖情况。
 *
 * <p>它用于区分“没有发现关系”与“分析器未启用、未实现或失败”，后者不得被渲染为业务能力不存在。</p>
 */
public enum CapabilityStatus {
    COMPLETE,
    PARTIAL,
    NOT_ENABLED,
    NOT_IMPLEMENTED,
    NOT_APPLICABLE,
    FAILED
}
