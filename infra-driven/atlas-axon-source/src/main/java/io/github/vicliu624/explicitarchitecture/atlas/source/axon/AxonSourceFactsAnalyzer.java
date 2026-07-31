package io.github.vicliu624.explicitarchitecture.atlas.source.axon;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Objects;

/** Axon 命令、事件、Saga 与投影源码关系解析适配器的发布锚点。 */
public final class AxonSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:axon-source"), "Axon 源码分析器", "命令、事件、投影与 Saga 源码事实");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return AnalyzerContribution.notImplemented(DESCRIPTOR.id().value(), request.scopeUri());
    }
}
