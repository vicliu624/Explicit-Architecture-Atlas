package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 引用操作成功后保证成立的规则 ID。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.OPERATION_CONTRACT)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface AtlasPostcondition {

    String[] rules();
}
