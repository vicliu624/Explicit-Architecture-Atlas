package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为第三方、反射或复杂封装操作提供可验证的最小语义摘要。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.OPERATION_CONTRACT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface AtlasOperationSummary {

    String id();

    String[] inputs() default {};

    String[] outputs() default {};

    String[] effects() default {};

    String[] failures() default {};
}
