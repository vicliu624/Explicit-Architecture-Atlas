package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 交给某个源码或运行时分析器的输入。 */
public record AnalysisRequest(
        AtlasId projectId,
        Path projectRoot,
        String sourceRevision,
        String scopeUri,
        ConventionProfile conventionProfile,
        List<Path> sourceRoots,
        List<Path> excludedPaths) {

    public AnalysisRequest {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot 不能为空").toAbsolutePath().normalize();
        Objects.requireNonNull(sourceRevision, "sourceRevision 不能为空");
        sourceRevision = sourceRevision.trim();
        if (sourceRevision.isEmpty()) {
            throw new IllegalArgumentException("sourceRevision 不能为空白");
        }
        Objects.requireNonNull(scopeUri, "scopeUri 不能为空");
        scopeUri = scopeUri.trim();
        if (!scopeUri.startsWith("project://")) {
            throw new IllegalArgumentException("scopeUri 必须使用 project:// 协议");
        }
        Objects.requireNonNull(conventionProfile, "conventionProfile 不能为空");
        sourceRoots = normalizeDescendants(projectRoot, sourceRoots, "sourceRoots");
        excludedPaths = normalizeDescendants(projectRoot, excludedPaths, "excludedPaths");
    }

    public AnalysisRequest(AtlasId projectId, Path projectRoot, String sourceRevision, ConventionProfile conventionProfile) {
        this(projectId, projectRoot, sourceRevision, "project://" + projectId.value() + "/", conventionProfile, List.of(), List.of());
    }

    public AnalysisRequest(
            AtlasId projectId,
            Path projectRoot,
            String sourceRevision,
            ConventionProfile conventionProfile,
            List<Path> sourceRoots,
            List<Path> excludedPaths) {
        this(projectId, projectRoot, sourceRevision, "project://" + projectId.value() + "/", conventionProfile, sourceRoots, excludedPaths);
    }

    public AnalysisRequest(AtlasId projectId, Path projectRoot, String sourceRevision) {
        this(projectId, projectRoot, sourceRevision, ConventionProfile.empty());
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
