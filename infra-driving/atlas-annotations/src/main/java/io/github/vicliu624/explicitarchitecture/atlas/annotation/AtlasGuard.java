package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 一次性解释一个团队自定义 guard 的参数和成功/失败语义。
 *
 * <p>分析器据此理解全部调用点，而不要求每一个 {@code require...} 调用重复标注。参数索引为零基；不存在的
 * 参数使用 {@code -1}。</p>
 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.DOMAIN_CONSTRAINT)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface AtlasGuard {

    String id();

    AtlasGuardKind kind();

    int subjectParameter() default -1;

    int predicateParameter() default -1;

    int patternParameter() default -1;

    int messageParameter() default -1;

    AtlasTruthValue acceptedWhen() default AtlasTruthValue.TRUE;

    AtlasGuardSuccess success() default AtlasGuardSuccess.VOID;

    Class<? extends Throwable> failureType() default RuntimeException.class;

    String businessMeaning() default "";
}
