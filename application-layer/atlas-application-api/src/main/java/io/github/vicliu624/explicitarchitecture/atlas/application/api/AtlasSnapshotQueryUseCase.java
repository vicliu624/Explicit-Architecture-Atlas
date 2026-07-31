package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Optional;

/** 按快照身份查询已经生成的 Atlas。 */
@FunctionalInterface
public interface AtlasSnapshotQueryUseCase {

    Optional<AtlasSnapshot> findById(String snapshotId);
}
