package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 一次性声明项目私有框架抽象的通用语义，分析器据此自动解释其所有调用点。
 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.COMPONENT_DEFINITION)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasAdapter {

    String id();

    AtlasAdapterKind kind();

    String framework() default "";

    String dispatchMethod() default "";

    String description() default "";
}
