package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注领域约束的业务含义和可验证表达式。
 *
 * <p>例如，身份格式约束可将正则表达式置于 {@link #expression()}；渲染器据此生成“为何不可录入”的业务说明。</p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface AtlasConstraint {

    String id();

    String name();

    ConstraintKind kind();

    String expression() default "";

    String businessMeaning();

    String enforcedBy() default "";
}
