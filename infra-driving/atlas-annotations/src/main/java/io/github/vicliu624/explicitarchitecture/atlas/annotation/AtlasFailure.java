package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 补充异常、错误码和失败条件的业务解释。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.OPERATION_CONTRACT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface AtlasFailure {

    Class<? extends Throwable> exception();

    String condition() default "";

    String errorCode() default "";

    String description() default "";
}
