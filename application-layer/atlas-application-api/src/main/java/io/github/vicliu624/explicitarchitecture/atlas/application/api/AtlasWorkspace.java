package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * 私有 Workspace 配置被适配器解析后的类型化形态。
 *
 * <p>Workspace 仅用于本机或部署环境的编译输入，绝不能被序列化进 {@code AtlasPackage} 或 Maven 工件。</p>
 */
public record AtlasWorkspace(
        String schemaVersion,
        AtlasId workspaceId,
        WorkspaceProject project,
        WorkspaceSources sources,
        Optional<WorkspaceContent> content,
        Optional<WorkspaceRuntime> runtime,
        Optional<Path> conventionProfileFile) {

    public AtlasWorkspace {
        Objects.requireNonNull(schemaVersion, "schemaVersion 不能为空");
        schemaVersion = schemaVersion.trim();
        if (schemaVersion.isEmpty()) {
            throw new IllegalArgumentException("schemaVersion 不能为空白");
        }
        Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        Objects.requireNonNull(project, "project 不能为空");
        Objects.requireNonNull(sources, "sources 不能为空");
        content = Objects.requireNonNull(content, "content 不能为空");
        runtime = Objects.requireNonNull(runtime, "runtime 不能为空");
        conventionProfileFile = Objects.requireNonNull(conventionProfileFile, "conventionProfileFile 不能为空")
                .map(path -> path.toAbsolutePath().normalize());
    }
}
