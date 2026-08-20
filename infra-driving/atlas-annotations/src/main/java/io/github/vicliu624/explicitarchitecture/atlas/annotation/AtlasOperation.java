package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 给重要方法、构造器或框架入口补充稳定业务目的和归属。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.OPERATION_CONTRACT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface AtlasOperation {

    String id();

    String name();

    String purpose();

    String capability() default "";

    String useCase() default "";
}
