package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

/** 生成 Atlas 快照的应用服务入口。 */
@FunctionalInterface
public interface AtlasBuildUseCase {

    AtlasPackage build(AtlasBuildRequest request);
}
