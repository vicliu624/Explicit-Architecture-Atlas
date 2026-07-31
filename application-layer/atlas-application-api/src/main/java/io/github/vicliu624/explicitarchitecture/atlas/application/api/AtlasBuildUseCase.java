package io.github.vicliu624.explicitarchitecture.atlas.application.api;

/** 生成 Atlas 快照的应用服务入口。 */
@FunctionalInterface
public interface AtlasBuildUseCase {

    AtlasSnapshot build(AtlasBuildRequest request);
}
