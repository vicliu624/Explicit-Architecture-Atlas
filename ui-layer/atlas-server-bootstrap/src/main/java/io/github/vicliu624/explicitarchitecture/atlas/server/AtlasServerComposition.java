package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;

import java.util.Objects;

/**
 * 服务端唯一允许认识具体适配器的组合根。
 *
 * <p>Web 框架启动代码在这里组合用例、源码分析器、文件存储和内容存储；其余模块保持可独立发布和测试。</p>
 */
public final class AtlasServerComposition {

    private final AtlasBuildUseCase atlasBuildUseCase;
    private final AtlasPackageQueryUseCase atlasPackageQueryUseCase;

    public AtlasServerComposition(
            AtlasBuildUseCase atlasBuildUseCase,
            AtlasPackageQueryUseCase atlasPackageQueryUseCase) {
        this.atlasBuildUseCase = Objects.requireNonNull(atlasBuildUseCase, "atlasBuildUseCase 不能为空");
        this.atlasPackageQueryUseCase = Objects.requireNonNull(atlasPackageQueryUseCase, "atlasPackageQueryUseCase 不能为空");
    }

    public AtlasBuildUseCase atlasBuildUseCase() {
        return atlasBuildUseCase;
    }

    public AtlasPackageQueryUseCase atlasPackageQueryUseCase() {
        return atlasPackageQueryUseCase;
    }
}
