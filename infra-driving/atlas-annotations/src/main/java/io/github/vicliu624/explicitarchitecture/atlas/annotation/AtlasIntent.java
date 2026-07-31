package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个注解为可由 Atlas 编译期处理器提取的显式意图。
 *
 * <p>使用 {@link RetentionPolicy#CLASS} 是为了让处理器能从依赖 JAR 中发现元注解；被项目实际使用的
 * {@code @Atlas...} 注解依然维持 {@code SOURCE} 保留策略，不会进入业务应用运行时。</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface AtlasIntent {

    AtlasIntentKind assetKind();
}
