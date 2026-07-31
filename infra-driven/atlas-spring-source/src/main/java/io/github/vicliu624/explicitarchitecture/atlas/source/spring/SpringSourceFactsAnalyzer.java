package io.github.vicliu624.explicitarchitecture.atlas.source.spring;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisResult;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;

import java.util.Objects;

/** Spring MVC、Bean 与端点调用关系解析适配器的发布锚点。 */
public final class SpringSourceFactsAnalyzer implements SourceFactsAnalyzer {

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return AnalysisResult.empty("spring-source");
    }
}
