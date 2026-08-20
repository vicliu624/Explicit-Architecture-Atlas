package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记对外兼容、版本协商或遗留契约的边界目标。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.EXTERNAL_BOUNDARY)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AtlasCompatibilityTarget {

    String id();

    String name();

    String targetSystem();

    String description() default "";
}
