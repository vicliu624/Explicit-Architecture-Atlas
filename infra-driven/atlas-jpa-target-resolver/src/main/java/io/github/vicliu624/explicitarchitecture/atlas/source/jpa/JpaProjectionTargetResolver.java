package io.github.vicliu624.explicitarchitecture.atlas.source.jpa;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisResult;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;

import java.util.Objects;

/** JPA 投影写入目标（实体、表、列）解析适配器的发布锚点。 */
public final class JpaProjectionTargetResolver implements SourceFactsAnalyzer {

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return AnalysisResult.empty("jpa-target-resolver");
    }
}
