package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshot;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshotQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshotRepository;

import java.util.Objects;
import java.util.Optional;

/** 通过快照仓储实现查询用例。 */
public final class RepositoryBackedAtlasSnapshotQuery implements AtlasSnapshotQueryUseCase {

    private final AtlasSnapshotRepository snapshotRepository;

    public RepositoryBackedAtlasSnapshotQuery(AtlasSnapshotRepository snapshotRepository) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository 不能为空");
    }

    @Override
    public Optional<AtlasSnapshot> findById(String snapshotId) {
        return snapshotRepository.findById(snapshotId);
    }
}
