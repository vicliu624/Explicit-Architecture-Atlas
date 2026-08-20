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

    /**
     * 按需读取一个 HTTP 入口的完整可达执行图。
     *
     * <p>专家首页只携带端点的轻量调用预览，避免大型工程的数百个入口一次性展开成不可传输的全图。调用方在用户
     * 明确选择入口后再通过此查询获取该入口的 {@link ExpertAtlasView.ExecutionGraph}。</p>
     */
    default Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTrace(AtlasId packageId, AtlasId endpointId) {
        return Optional.empty();
    }

    /** 以稳定的 Java handler 签名或唯一的 {@code Type#method} 前缀定位端点；用于按需追踪。 */
    default Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTraceByHandler(AtlasId packageId, String handlerQualifiedName) {
        return Optional.empty();
    }

    Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId);
}
