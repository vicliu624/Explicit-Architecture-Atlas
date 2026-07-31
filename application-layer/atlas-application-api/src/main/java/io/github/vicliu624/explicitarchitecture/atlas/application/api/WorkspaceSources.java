package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 项目源码、源码归档和编译期意图 Manifest 的私有输入位置。 */
public record WorkspaceSources(Path projectRoot, List<Path> sourceArchives, List<Path> intentManifests) {

    public WorkspaceSources {
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot 不能为空").toAbsolutePath().normalize();
        sourceArchives = normalizePaths(sourceArchives, "sourceArchives");
        intentManifests = normalizePaths(intentManifests, "intentManifests");
    }

    private static List<Path> normalizePaths(List<Path> paths, String name) {
        return List.copyOf(Objects.requireNonNull(paths, name + " 不能为空").stream()
                .map(path -> Objects.requireNonNull(path, name + " 不能包含 null").toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .toList());
    }
}
