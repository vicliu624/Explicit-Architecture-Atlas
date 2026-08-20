package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地查询服务的进程内只读 Package 缓存。
 *
 * <p>文件仓储每次查询都需要反序列化完整 Package。对大工程而言，前端逐步展开调用链、资产详情和源码时若重复读取，
 * 延迟会随 Package 大小线性放大。服务启动后的 Package 是不可变值对象，因此以 packageId 缓存命中不会改变
 * 查询语义；发布新 Package 后重启本地查询服务即可明确地切换到新版本。</p>
 */
final class CachingAtlasPackageQuery implements AtlasPackageQueryUseCase {

    private final AtlasPackageQueryUseCase delegate;
    private final Map<AtlasId, AtlasPackage> cache = new ConcurrentHashMap<>();

    CachingAtlasPackageQuery(AtlasPackageQueryUseCase delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
    }

    @Override
    public Optional<AtlasPackage> findById(AtlasId packageId) {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        AtlasPackage cached = cache.get(packageId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return delegate.findById(packageId).map(atlasPackage -> {
            AtlasPackage prior = cache.putIfAbsent(packageId, atlasPackage);
            return prior == null ? atlasPackage : prior;
        });
    }
}
