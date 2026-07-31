package io.github.vicliu624.explicitarchitecture.atlas.http;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.util.Optional;

/**
 * HTTP 驱动适配器应实现的 Package 查询边界。
 *
 * <p>本模块不绑定 Spring Web：Spring、Javalin 或其他 HTTP 框架可分别实现该边界而不污染核心模型。</p>
 */
@FunctionalInterface
public interface AtlasPackageHttpQuery {

    Optional<AtlasPackage> getPackage(AtlasId packageId);
}
