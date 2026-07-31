package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeOverlay;

import java.util.List;
import java.util.Objects;

/** 运行态编译结果；Overlay 可保存进 Package，健康摘要供运维/API 渲染立即消费。 */
public record RuntimeOverlayCompileResult(
        RuntimeOverlay overlay,
        List<RuntimeAssetHealth> health,
        List<AtlasDiagnostic> diagnostics) {

    public RuntimeOverlayCompileResult {
        Objects.requireNonNull(overlay, "overlay 不能为空");
        health = List.copyOf(Objects.requireNonNull(health, "health 不能为空"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空"));
    }
}
