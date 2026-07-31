package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注一个限界上下文及其面向业务的说明。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.BUSINESS_CONCEPT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface AtlasContext {

    String id();

    String name();

    String description() default "";
}
