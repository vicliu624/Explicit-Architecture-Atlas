package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 识别可跨领域对象复用的规则集合，避免其被误当作普通工具类。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.DOMAIN_CONSTRAINT)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasRuleSet {

    String id();

    String name();

    String purpose();
}
