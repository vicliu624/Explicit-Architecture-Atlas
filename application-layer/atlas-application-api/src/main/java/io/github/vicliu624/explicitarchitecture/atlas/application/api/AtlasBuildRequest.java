package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;

import java.nio.file.Path;
import java.util.Objects;

/** 请求编译一个可离线保存的 Atlas Package。 */
public record AtlasBuildRequest(
        AtlasId packageId,
        AtlasId projectId,
        Path projectRoot,
        String sourceRevision,
        ConventionProfile conventionProfile,
        IntentBundle intents,
        ContentBundle content) {

    public AtlasBuildRequest {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Objects.requireNonNull(projectId, "projectId 不能为空");
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot 不能为空").toAbsolutePath().normalize();
        Objects.requireNonNull(sourceRevision, "sourceRevision 不能为空");
        sourceRevision = sourceRevision.trim();
        if (sourceRevision.isEmpty()) {
            throw new IllegalArgumentException("sourceRevision 不能为空白");
        }
        Objects.requireNonNull(conventionProfile, "conventionProfile 不能为空");
        Objects.requireNonNull(intents, "intents 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
    }
}
