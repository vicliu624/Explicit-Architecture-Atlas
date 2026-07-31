package io.github.vicliu624.explicitarchitecture.atlas.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注读模型投影的用途；实际写入目标由框架分析器补充。 */
@Documented
@AtlasIntent(assetKind = AtlasIntentKind.PROJECTION)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AtlasProjection {

    String id();

    String name();

    String purpose();
}
