package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注领域事件及其面向业务的事实描述。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasEvent {

    String id();

    String name();

    String fact();

    String[] payload() default {};
}
