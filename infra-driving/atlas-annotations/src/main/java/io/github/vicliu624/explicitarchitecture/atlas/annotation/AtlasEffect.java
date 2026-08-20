package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为难以由静态源码可靠推导的重要副作用补充声明。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.OPERATION_CONTRACT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface AtlasEffect {

    AtlasEffectKind kind();

    String target() default "";

    String condition() default "";

    String description() default "";
}
