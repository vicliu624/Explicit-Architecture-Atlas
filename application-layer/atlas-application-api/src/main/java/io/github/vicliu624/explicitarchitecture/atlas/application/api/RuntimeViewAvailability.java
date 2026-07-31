package io.github.vicliu624.explicitarchitecture.atlas.application.api;

/** 角色化运维视图中运行态观测的可用性，和某个资产的健康状态严格区分。 */
public enum RuntimeViewAvailability {
    /** Package 含有一次 Runtime Overlay；资产状态可据此解释。 */
    AVAILABLE,
    /** 尚未叠加 Runtime Overlay，不能把这一情况伪装成 UNKNOWN 或 SUCCEEDED。 */
    NOT_CAPTURED
}
