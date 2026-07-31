package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注 Process Manager / Saga 的跨聚合业务职责。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasProcessManager {

    String id();

    String name();

    String responsibility();

    String completionCondition() default "";
}
