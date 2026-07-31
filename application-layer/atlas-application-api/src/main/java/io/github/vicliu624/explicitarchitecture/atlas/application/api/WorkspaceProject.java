package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Objects;

/** Workspace 所注册的一个被分析项目。 */
public record WorkspaceProject(AtlasId id, String displayName) {

    public WorkspaceProject {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(displayName, "displayName 不能为空");
        displayName = displayName.trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName 不能为空白");
        }
    }
}
