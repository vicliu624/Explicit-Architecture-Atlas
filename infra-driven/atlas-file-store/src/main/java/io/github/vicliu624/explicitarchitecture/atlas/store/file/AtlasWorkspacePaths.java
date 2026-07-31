package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** 本地 Workspace 中 Atlas 数据与日志的约定路径。 */
public final class AtlasWorkspacePaths {

    private final Path workspaceRoot;

    public AtlasWorkspacePaths(Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot 不能为空").toAbsolutePath().normalize();
    }

    public Path packagesDirectory() {
        return workspaceRoot.resolve("packages");
    }

    /**
     * 用 URL-safe Base64 生成跨平台文件名；AtlasId 本身仍完整保留在 JSON Manifest 中。
     * Windows 不允许 {@code :}，故不能直接将业务身份当作文件名。
     */
    public Path packageFile(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            throw new IllegalArgumentException("packageId 不能为空");
        }
        String safeName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(packageId.getBytes(StandardCharsets.UTF_8));
        return packagesDirectory().resolve(safeName + ".atlas.json");
    }

    public Path sourceCacheDirectory() {
        return workspaceRoot.resolve("source-cache");
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }
}
