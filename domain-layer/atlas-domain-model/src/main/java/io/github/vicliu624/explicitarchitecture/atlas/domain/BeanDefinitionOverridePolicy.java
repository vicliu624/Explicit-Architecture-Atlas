package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 同名 Bean 定义在一个应用上下文中的覆盖规则。 */
public enum BeanDefinitionOverridePolicy {

    /** 未获得启动配置或运行时证据，不能把同名候选任意排序后选一个。 */
    UNSPECIFIED,
    /** Spring 应拒绝重复定义；该上下文不能宣称已经得到可启动的唯一 Bean 图。 */
    FORBID,
    /** Spring 允许覆盖，但实际注册顺序仍须由显式覆盖映射或运行时证据证明。 */
    ALLOW
}
