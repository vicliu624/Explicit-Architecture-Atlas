package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;

import java.util.List;
import java.util.Objects;

/** 单一分析器提取出的事实片段。 */
public record AnalysisResult(String analyzerId, List<ArchitectureAsset> assets) {

    public AnalysisResult {
        Objects.requireNonNull(analyzerId, "analyzerId 不能为空");
        if (analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId 不能为空白");
        }
        assets = List.copyOf(Objects.requireNonNull(assets, "assets 不能为空"));
    }

    public static AnalysisResult empty(String analyzerId) {
        return new AnalysisResult(analyzerId, List.of());
    }
}
