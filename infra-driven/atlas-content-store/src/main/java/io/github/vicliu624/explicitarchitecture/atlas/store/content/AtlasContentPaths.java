package io.github.vicliu624.explicitarchitecture.atlas.store.content;

import java.nio.file.Path;
import java.util.Objects;

/** 业务说明、运维建议等人工维护内容的安全路径解析器。 */
public final class AtlasContentPaths {

    private final Path contentRoot;

    public AtlasContentPaths(Path contentRoot) {
        this.contentRoot = Objects.requireNonNull(contentRoot, "contentRoot 不能为空").toAbsolutePath().normalize();
    }

    public Path resolve(String relativeContentPath) {
        Path resolved = contentRoot.resolve(relativeContentPath).normalize();
        if (!resolved.startsWith(contentRoot)) {
            throw new IllegalArgumentException("内容路径不能越出内容根目录");
        }
        return resolved;
    }
}
