package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为复杂或复用规则提供稳定的业务 ID、名称和可读说明。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.DOMAIN_CONSTRAINT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface AtlasRule {

    String id();

    String name();

    ConstraintKind kind() default ConstraintKind.CUSTOM;

    String description();
}
