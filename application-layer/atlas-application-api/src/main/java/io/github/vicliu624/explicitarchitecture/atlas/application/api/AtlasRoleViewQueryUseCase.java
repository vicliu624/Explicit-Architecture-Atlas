package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Optional;

/**
 * 从一份已经生成的 Atlas Package 组装角色化读取模型。
 *
 * <p>该边界是 HTTP、桌面端或静态前端的唯一解释来源。调用者不得自行扫描源码、读取 Axon 存储、推断投影落点或
 * 把运行态缺失解释为成功。</p>
 */
public interface AtlasRoleViewQueryUseCase {

    Optional<AtlasViewSummary> findOverview(AtlasId packageId);

    Optional<BusinessAtlasView> findBusinessView(AtlasId packageId);

    Optional<OperationsAtlasView> findOperationsView(AtlasId packageId);

    Optional<ExpertAtlasView> findExpertView(AtlasId packageId);

    Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId);
}
