package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 请求编译一个可离线保存的 Atlas Package。 */
public record AtlasBuildRequest(
        AtlasId packageId,
        AtlasId projectId,
        Path projectRoot,
        String sourceRevision,
        ConventionProfile conventionProfile,
        IntentBundle intents,
        ContentBundle content,
        List<Path> sourceRoots,
        List<Path> excludedPaths,
        List<ConfigurationScenario> scenarios) {

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
        sourceRoots = normalizeDescendants(projectRoot, sourceRoots, "sourceRoots");
        excludedPaths = normalizeDescendants(projectRoot, excludedPaths, "excludedPaths");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios 不能为空").stream()
                .map(scenario -> Objects.requireNonNull(scenario, "scenario 不能包含 null"))
                .sorted(Comparator.comparing(ConfigurationScenario::id)).toList());
    }

    public AtlasBuildRequest(
            AtlasId packageId,
            AtlasId projectId,
            Path projectRoot,
            String sourceRevision,
            ConventionProfile conventionProfile,
            IntentBundle intents,
            ContentBundle content) {
        this(packageId, projectId, projectRoot, sourceRevision, conventionProfile, intents, content, List.of(), List.of(), List.of());
    }

    public AtlasBuildRequest(
            AtlasId packageId,
            AtlasId projectId,
            Path projectRoot,
            String sourceRevision,
            ConventionProfile conventionProfile,
            IntentBundle intents,
            ContentBundle content,
            List<Path> sourceRoots,
            List<Path> excludedPaths) {
        this(packageId, projectId, projectRoot, sourceRevision, conventionProfile, intents, content, sourceRoots, excludedPaths, List.of());
    }

    private static List<Path> normalizeDescendants(Path projectRoot, List<Path> paths, String field) {
        return List.copyOf(Objects.requireNonNull(paths, field + " 不能为空").stream()
                .map(path -> Objects.requireNonNull(path, field + " 不能包含 null").toAbsolutePath().normalize())
                .peek(path -> {
                    if (!path.startsWith(projectRoot)) {
                        throw new IllegalArgumentException(field + " 必须位于 projectRoot 内: " + path);
                    }
                })
                .distinct().sorted(Comparator.comparing(Path::toString)).toList());
    }
}
