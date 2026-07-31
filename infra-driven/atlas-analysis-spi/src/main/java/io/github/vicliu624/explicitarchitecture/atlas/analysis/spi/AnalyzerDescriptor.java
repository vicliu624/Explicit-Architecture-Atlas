package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Objects;

/** 一个可插拔分析器的稳定身份与职责声明。 */
public record AnalyzerDescriptor(AtlasId id, String displayName, String capability) {

    public AnalyzerDescriptor {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(displayName, "displayName 不能为空");
        displayName = displayName.trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName 不能为空白");
        }
        Objects.requireNonNull(capability, "capability 不能为空");
        capability = capability.trim();
        if (capability.isEmpty()) {
            throw new IllegalArgumentException("capability 不能为空白");
        }
    }
}
