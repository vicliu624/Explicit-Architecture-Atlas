package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 运维视图展示给投影或 Process Manager 的当前健康结论。 */
public enum RuntimeHealthStatus {
    SUCCEEDED,
    FAILED,
    LAGGING,
    RETRYING,
    /** 本次采集范围没有提供可唯一关联的运行态记录；不表示处理成功。 */
    UNKNOWN
}
