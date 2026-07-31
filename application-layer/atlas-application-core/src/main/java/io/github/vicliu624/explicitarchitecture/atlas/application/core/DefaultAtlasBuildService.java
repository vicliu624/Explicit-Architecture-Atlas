package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshot;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshotRepository;

import java.util.Objects;

/**
 * Atlas 构建应用服务的最小编排实现。
 *
 * <p>后续分析器在此处汇集事实与意图；当前骨架先确保依赖方向和持久化端口可被独立复用。</p>
 */
public final class DefaultAtlasBuildService implements AtlasBuildUseCase {

    private final AtlasSnapshotRepository snapshotRepository;

    public DefaultAtlasBuildService(AtlasSnapshotRepository snapshotRepository) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository 不能为空");
    }

    @Override
    public AtlasSnapshot build(AtlasBuildRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        AtlasSnapshot snapshot = AtlasSnapshot.emptyFor(request.projectId());
        snapshotRepository.save(snapshot);
        return snapshot;
    }
}
