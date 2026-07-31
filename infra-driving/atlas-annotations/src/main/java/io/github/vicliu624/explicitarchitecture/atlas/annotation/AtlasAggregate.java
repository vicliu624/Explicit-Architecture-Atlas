package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注聚合根、其业务名称和承担的一致性边界。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasAggregate {

    String id();

    String name();

    String responsibility();
}
