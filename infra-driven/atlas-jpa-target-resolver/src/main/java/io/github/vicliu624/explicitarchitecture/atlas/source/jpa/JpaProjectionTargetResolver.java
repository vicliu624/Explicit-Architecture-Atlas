package io.github.vicliu624.explicitarchitecture.atlas.source.jpa;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Objects;

/** JPA 投影写入目标（实体、表、列）解析适配器的发布锚点。 */
public final class JpaProjectionTargetResolver implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:jpa-target-resolver"), "JPA 目标解析器", "投影、实体、表与列写入目标事实");

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
