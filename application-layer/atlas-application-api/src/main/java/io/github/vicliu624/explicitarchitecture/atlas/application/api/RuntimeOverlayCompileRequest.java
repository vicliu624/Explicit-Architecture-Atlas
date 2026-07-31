package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignalBatch;

import java.util.Objects;

/** 将一批脱敏运行态信号关联至既有静态 Atlas Package 的请求。 */
public record RuntimeOverlayCompileRequest(AtlasPackage atlasPackage, RuntimeSignalBatch signalBatch) {

    public RuntimeOverlayCompileRequest {
        Objects.requireNonNull(atlasPackage, "atlasPackage 不能为空");
        Objects.requireNonNull(signalBatch, "signalBatch 不能为空");
    }
}
