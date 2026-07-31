package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;

import java.util.List;
import java.util.Objects;

/** 事实、意图和内容归一化为设计图谱后的结果。 */
public record DesignAtlasResolution(AtlasGraph designAtlas, List<AtlasDiagnostic> diagnostics) {

    public DesignAtlasResolution {
        Objects.requireNonNull(designAtlas, "designAtlas 不能为空");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空"));
    }
}
