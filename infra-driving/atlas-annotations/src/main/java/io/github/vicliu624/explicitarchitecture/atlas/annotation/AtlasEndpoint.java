package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注一个外部接口操作的业务目的和调用入口。 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface AtlasEndpoint {

    String id();

    String name();

    String purpose();
}
