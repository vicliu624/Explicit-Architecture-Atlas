package io.github.vicliu624.explicitarchitecture.atlas.domain;

/**
 * 一条静态方法调用在命名配置场景中的分派结论。
 *
 * <p>{@link #STATIC_ONLY} 明确表示 Atlas 没有把该调用和一个受容器管理的接收者关联起来；它不是“调用已确认”。
 * {@link #RESOLVED} 仅在注入选择和本地实现方法均能唯一确定时使用。</p>
 */
public enum ScenarioInvocationStatus {
    /** 源码中存在调用，但没有可证明的 DI 接收者绑定。 */
    STATIC_ONLY,
    /** 调用所属组件在此场景未激活。 */
    EXCLUDED,
    /** 场景、注入或 Java 分派尚不能唯一确定。 */
    UNRESOLVED,
    /** 注入目标和本地实际实现方法已唯一确定。 */
    RESOLVED
}
