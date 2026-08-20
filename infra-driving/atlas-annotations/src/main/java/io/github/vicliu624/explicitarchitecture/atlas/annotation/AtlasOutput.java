package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明方法返回值或构造成功结果的业务语义。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.SEMANTIC_VALUE)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE_USE})
public @interface AtlasOutput {

    String id();

    String name();

    String sourceParameter() default "";

    String description() default "";
}
