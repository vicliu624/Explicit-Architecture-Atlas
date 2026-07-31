package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.util.Objects;
import java.util.Optional;

/** 通过 Package 仓储实现查询用例。 */
public final class RepositoryBackedAtlasPackageQuery implements AtlasPackageQueryUseCase {

    private final AtlasPackageRepository packageRepository;

    public RepositoryBackedAtlasPackageQuery(AtlasPackageRepository packageRepository) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "packageRepository 不能为空");
    }

    @Override
    public Optional<AtlasPackage> findById(AtlasId packageId) {
        return packageRepository.findById(packageId);
    }
}
