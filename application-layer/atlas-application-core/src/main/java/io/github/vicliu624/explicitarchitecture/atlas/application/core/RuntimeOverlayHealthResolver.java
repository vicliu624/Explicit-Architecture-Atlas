package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHealthStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeObservation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeOverlay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 根据已持久化的 Runtime Overlay 重建运维健康结论。
 *
 * <p>健康结论不是新的设计事实，因而不写入 {@code AtlasPackage}；它由静态 Projection / Process Manager、
 * Overlay 和投影落点在查询时确定性导出。编译 Overlay 和提供运维视图共用本类，避免状态文案或影响目标语义漂移。</p>
 */
final class RuntimeOverlayHealthResolver {

    List<RuntimeAssetHealth> assess(AtlasGraph graph, RuntimeOverlay overlay) {
        Objects.requireNonNull(graph, "graph 不能为空");
        Objects.requireNonNull(overlay, "overlay 不能为空");
        Index index = new Index(graph);
        Map<AtlasId, List<RuntimeObservation>> bySubject = overlay.observations().stream()
                .collect(Collectors.groupingBy(RuntimeObservation::subjectId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        return index.observableAssets().stream()
                .map(asset -> health(asset, latest(bySubject.getOrDefault(asset.id(), List.of())), overlay.capturedAt(), index))
                .sorted(Comparator.comparing(RuntimeAssetHealth::subjectId))
                .toList();
    }

    private static RuntimeAssetHealth health(
            ArchitectureAsset asset,
            Optional<RuntimeObservation> latest,
            Instant assessedAt,
            Index index) {
        RuntimeHealthStatus status = latest.map(observation -> statusOf(observation.observationType())).orElse(RuntimeHealthStatus.UNKNOWN);
        List<AtlasId> impactedTargets = index.impactedDataTargets(asset.id());
        return new RuntimeAssetHealth(
                asset.id(),
                status,
                assessedAt,
                latest,
                impactedTargets,
                summary(asset, status),
                suggestedAction(status));
    }

    private static Optional<RuntimeObservation> latest(List<RuntimeObservation> observations) {
        return observations.stream().max(Comparator.comparing(RuntimeObservation::observedAt).thenComparing(RuntimeObservation::id));
    }

    private static RuntimeHealthStatus statusOf(String observationType) {
        int delimiter = observationType.lastIndexOf('_');
        if (delimiter < 0 || delimiter == observationType.length() - 1) {
            throw new IllegalStateException("不支持的 Atlas 运行态观测类型: " + observationType);
        }
        try {
            return RuntimeHealthStatus.valueOf(observationType.substring(delimiter + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("不支持的 Atlas 运行态观测类型: " + observationType, exception);
        }
    }

    private static String summary(ArchitectureAsset asset, RuntimeHealthStatus status) {
        String assetLabel = asset instanceof Projection ? "投影“" + asset.name() + "”" : "自动流程“" + asset.name() + "”";
        return switch (status) {
            case SUCCEEDED -> assetLabel + "最近一次已处理完成；相关业务数据应已按静态目标更新。";
            case FAILED -> assetLabel + "最近一次处理失败；业务数据可能尚未反映该事件的结果。";
            case LAGGING -> assetLabel + "当前存在处理延迟或积压；业务数据可能暂未可见。";
            case RETRYING -> assetLabel + "正在重试处理；应持续观察结果和重试次数。";
            case UNKNOWN -> assetLabel + "在本次采集范围内没有可关联的运行态记录，无法判断是否已处理。";
        };
    }

    private static String suggestedAction(RuntimeHealthStatus status) {
        return switch (status) {
            case SUCCEEDED -> "无需处置；如业务页面仍未更新，请核对查询条件和数据可见范围。";
            case FAILED -> "检查处理器的错误分类、重试策略以及静态图中标示的写入目标。";
            case LAGGING -> "检查事件积压、处理组吞吐和写入目标的可用性。";
            case RETRYING -> "观察重试次数和最新错误分类；超过运维阈值后按故障流程介入。";
            case UNKNOWN -> "确认运行态采集已启用、导出范围覆盖该处理器，并避免把未知当作正常。";
        };
    }

    private static final class Index {

        private final Map<AtlasId, ArchitectureAsset> nodes;
        private final List<AtlasRelation> relations;

        private Index(AtlasGraph graph) {
            nodes = graph.nodes().stream()
                    .collect(Collectors.toMap(ArchitectureAsset::id, asset -> asset, (first, ignored) -> first, LinkedHashMap::new));
            relations = graph.relations();
        }

        private List<ArchitectureAsset> observableAssets() {
            return nodes.values().stream()
                    .filter(asset -> asset instanceof Projection || asset instanceof ProcessManager)
                    .sorted(Comparator.comparing(ArchitectureAsset::id))
                    .toList();
        }

        private List<AtlasId> impactedDataTargets(AtlasId assetId) {
            return relations.stream()
                    .filter(relation -> relation.sourceId().equals(assetId))
                    .filter(relation -> relation.kind() == RelationKind.PROJECTS_TO || relation.kind() == RelationKind.WRITES_TO)
                    .map(AtlasRelation::targetId)
                    .filter(nodes::containsKey)
                    .filter(target -> nodes.get(target) instanceof DataTarget)
                    .distinct()
                    .sorted()
                    .toList();
        }
    }
}
