package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/** 将完整 Atlas Package 原子写入私有 Workspace，并可在新进程中读回。 */
public final class AtlasPackageFileRepository implements AtlasPackageRepository {

    private final AtlasWorkspacePaths paths;
    private final AtlasPackageJsonCodec codec = new AtlasPackageJsonCodec();

    public AtlasPackageFileRepository(AtlasWorkspacePaths paths) {
        this.paths = Objects.requireNonNull(paths, "paths 不能为空");
    }

    @Override
    public void save(AtlasPackage atlasPackage) {
        Objects.requireNonNull(atlasPackage, "atlasPackage 不能为空");
        Path destination = paths.packageFile(atlasPackage.manifest().packageId().value());
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
            try {
                codec.write(temporary, atlasPackage);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入 Atlas Package: " + destination, exception);
        }
    }

    @Override
    public Optional<AtlasPackage> findById(AtlasId packageId) {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Path source = paths.packageFile(packageId.value());
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        try {
            return Optional.of(codec.read(source));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Atlas Package: " + source, exception);
        }
    }

    public Path fileOf(AtlasId packageId) {
        return paths.packageFile(Objects.requireNonNull(packageId, "packageId 不能为空").value());
    }
}
