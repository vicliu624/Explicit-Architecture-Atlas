package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Runtime Overlay 是否允许在该 Workspace 中被采集；连接凭据不属于本对象。 */
public record WorkspaceRuntime(boolean enabled, List<Path> observationFiles) {

    public WorkspaceRuntime {
        observationFiles = List.copyOf(Objects.requireNonNull(observationFiles, "observationFiles 不能为空").stream()
                .map(path -> Objects.requireNonNull(path, "observationFiles 不能包含 null").toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .toList());
    }
}
