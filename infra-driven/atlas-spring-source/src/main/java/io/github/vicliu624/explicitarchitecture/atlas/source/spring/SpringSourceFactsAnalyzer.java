package io.github.vicliu624.explicitarchitecture.atlas.source.spring;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Objects;

/** Spring MVC、Bean 与端点调用关系解析适配器的发布锚点。 */
public final class SpringSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:spring-source"), "Spring 源码分析器", "Spring Endpoint、Bean 与调用事实");

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
