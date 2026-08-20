package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个具有稳定业务语义、表示和不变量的领域原语。
 *
 * <p>它不要求所有单字段类型都标注；应仅用于需要业务解释、追踪和绕过检测的基础业务值。</p>
 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.SEMANTIC_VALUE)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AtlasDomainPrimitive {

    String id();

    String name();

    PrimitiveCategory category() default PrimitiveCategory.CUSTOM;

    String valueProperty() default "value";

    Class<?> baseType() default Object.class;

    String description();
}
