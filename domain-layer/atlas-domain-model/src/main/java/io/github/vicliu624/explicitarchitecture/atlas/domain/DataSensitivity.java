package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 导出、渲染或运行态采集时的默认数据可见性分类。 */
public enum DataSensitivity {
    PUBLIC,
    INTERNAL,
    PII,
    SECRET,
    UNKNOWN
}
