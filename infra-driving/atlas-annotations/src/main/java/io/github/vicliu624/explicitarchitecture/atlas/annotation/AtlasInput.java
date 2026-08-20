package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为参数、record component 或字段声明无法从 Java 类型推导的语义值身份。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.SEMANTIC_VALUE)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.TYPE_USE})
public @interface AtlasInput {

    String id();

    String name();

    PrimitiveCategory category() default PrimitiveCategory.CUSTOM;

    String description() default "";
}
