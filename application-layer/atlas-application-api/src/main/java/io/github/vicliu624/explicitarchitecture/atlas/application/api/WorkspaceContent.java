package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.nio.file.Path;
import java.util.Objects;

/** 业务/运维内容包的受控根目录。 */
public record WorkspaceContent(Path root) {

    public WorkspaceContent {
        root = Objects.requireNonNull(root, "root 不能为空").toAbsolutePath().normalize();
    }
}
