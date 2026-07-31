package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.util.Optional;

/** 按稳定身份查询已经生成的 Atlas Package。 */
@FunctionalInterface
public interface AtlasPackageQueryUseCase {

    Optional<AtlasPackage> findById(AtlasId packageId);
}
