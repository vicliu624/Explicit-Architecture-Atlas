package io.github.vicliu624.explicitarchitecture.atlas.core;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;

import java.util.List;
import java.util.Objects;

/**
 * 面向 Maven 使用者的零框架 Atlas 核心 Facade。
 *
 * <p>调用方通过显式提供 adapter 分析器决定能力集合；本模块不会因为创建构建服务而加载或传递引入 Spring、
 * Axon、JPA/JDBC、JSON、HTTP 或 Maven Plugin。</p>
 */
public final class AtlasCore {

    private AtlasCore() {
    }

    public static AtlasBuildUseCase buildService(
            AtlasPackageRepository repository,
            List<? extends SourceFactsAnalyzer> analyzers) {
        Objects.requireNonNull(repository, "repository 不能为空");
        return new DefaultAtlasBuildService(repository, List.copyOf(Objects.requireNonNull(analyzers, "analyzers 不能为空")),
                new DefaultDesignAtlasResolver());
    }
}
