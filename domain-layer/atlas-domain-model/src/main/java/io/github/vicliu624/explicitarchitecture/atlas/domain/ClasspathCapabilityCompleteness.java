package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 场景提供的 classpath 能力集合是否已穷尽；缺少该证据时不能把“未列出”解释为“类不存在”。 */
public enum ClasspathCapabilityCompleteness {
    PARTIAL,
    COMPLETE
}
