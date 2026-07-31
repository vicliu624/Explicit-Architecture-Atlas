package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 跨聚合、跨上下文自动流程的协调者，技术上可由 Saga 实现。 */
public record ProcessManager(
        AssetMetadata metadata,
        String responsibility,
        String completionCondition) implements ArchitectureAsset {

    public ProcessManager {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(responsibility, "responsibility 不能为空");
        responsibility = responsibility.trim();
        if (responsibility.isEmpty()) {
            throw new IllegalArgumentException("responsibility 不能为空白");
        }
        completionCondition = completionCondition == null ? "" : completionCondition.trim();
    }

    @Override
    public AssetKind kind() {
        return AssetKind.PROCESS_MANAGER;
    }
}
