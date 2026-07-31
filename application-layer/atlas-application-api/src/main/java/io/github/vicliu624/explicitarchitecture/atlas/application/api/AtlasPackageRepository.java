package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.util.Optional;

/** Atlas Package 持久化端口；文件、对象存储或数据库均可实现它。 */
public interface AtlasPackageRepository {

    void save(AtlasPackage atlasPackage);

    Optional<AtlasPackage> findById(AtlasId packageId);
}
