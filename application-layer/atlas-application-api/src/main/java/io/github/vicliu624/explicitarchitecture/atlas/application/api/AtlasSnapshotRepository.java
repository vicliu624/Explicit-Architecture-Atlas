package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Optional;

/** Atlas 快照持久化端口；文件、对象存储或数据库均可实现它。 */
public interface AtlasSnapshotRepository {

    void save(AtlasSnapshot snapshot);

    Optional<AtlasSnapshot> findById(String snapshotId);
}
