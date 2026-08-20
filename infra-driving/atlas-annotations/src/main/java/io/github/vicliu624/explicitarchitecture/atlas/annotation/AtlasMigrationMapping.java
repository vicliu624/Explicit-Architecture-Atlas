package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明新旧实现、接口或模型的稳定迁移对应关系。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.CODE_ELEMENT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface AtlasMigrationMapping {

    String id();

    String legacyTarget();

    String relationship() default "REPLACES";

    String description() default "";
}
