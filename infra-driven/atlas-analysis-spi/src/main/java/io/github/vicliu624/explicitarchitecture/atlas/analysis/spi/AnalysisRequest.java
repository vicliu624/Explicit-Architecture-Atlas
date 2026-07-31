package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

import java.nio.file.Path;
import java.util.Objects;

/** 交给某个源码或运行时分析器的输入。 */
public record AnalysisRequest(String projectId, Path projectRoot) {

    public AnalysisRequest {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(projectRoot, "projectRoot 不能为空");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("projectId 不能为空白");
        }
    }
}
