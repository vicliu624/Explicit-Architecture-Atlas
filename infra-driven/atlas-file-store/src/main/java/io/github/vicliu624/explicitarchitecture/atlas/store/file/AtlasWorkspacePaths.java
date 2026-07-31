package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import java.nio.file.Path;
import java.util.Objects;

/** 本地 Workspace 中 Atlas 数据与日志的约定路径。 */
public final class AtlasWorkspacePaths {

    private final Path workspaceRoot;

    public AtlasWorkspacePaths(Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot 不能为空").toAbsolutePath().normalize();
    }

    public Path snapshotsDirectory() {
        return workspaceRoot.resolve("snapshots");
    }

    public Path snapshotFile(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank() || snapshotId.contains("/") || snapshotId.contains("\\")) {
            throw new IllegalArgumentException("snapshotId 必须是单一文件名");
        }
        return snapshotsDirectory().resolve(snapshotId + ".json");
    }

    public Path sourceCacheDirectory() {
        return workspaceRoot.resolve("source-cache");
    }
}
