package io.github.vicliu624.explicitarchitecture.atlas.source.axon;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisResult;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;

import java.util.Objects;

/** Axon 命令、事件、Saga 与投影源码关系解析适配器的发布锚点。 */
public final class AxonSourceFactsAnalyzer implements SourceFactsAnalyzer {

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return AnalysisResult.empty("axon-source");
    }
}
