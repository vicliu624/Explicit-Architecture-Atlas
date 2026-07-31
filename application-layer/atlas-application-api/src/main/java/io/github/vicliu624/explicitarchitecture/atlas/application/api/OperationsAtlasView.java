package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.List;
import java.util.Objects;

/** 面向运维人员的投影和 Process Manager 处理状态、影响范围与处置建议。 */
public record OperationsAtlasView(
        AtlasViewSummary summary,
        RuntimeViewAvailability runtimeAvailability,
        String message,
        List<AssetHealth> assets,
        List<AtlasDiagnosticView> diagnostics) {

    public OperationsAtlasView {
        Objects.requireNonNull(summary, "summary 不能为空");
        Objects.requireNonNull(runtimeAvailability, "runtimeAvailability 不能为空");
        message = message == null ? "" : message.trim();
        assets = List.copyOf(Objects.requireNonNull(assets, "assets 不能为空"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空"));
    }

    /** 某个可观测资产的最近结论；不携带原始事件 payload 或异常正文。 */
    public record AssetHealth(
            AtlasAssetReference asset,
            String status,
            String observedAt,
            String observationType,
            List<AtlasProperty> safeAttributes,
            List<AtlasAssetReference> impactedDataTargets,
            String summary,
            String suggestedAction,
            List<AtlasNarrative> narratives,
            List<AtlasEvidenceView> evidence) {
        public AssetHealth {
            Objects.requireNonNull(asset, "asset 不能为空");
            status = required(status, "status");
            observedAt = observedAt == null ? "" : observedAt.trim();
            observationType = observationType == null ? "" : observationType.trim();
            safeAttributes = immutable(safeAttributes, "safeAttributes");
            impactedDataTargets = immutable(impactedDataTargets, "impactedDataTargets");
            summary = required(summary, "summary");
            suggestedAction = required(suggestedAction, "suggestedAction");
            narratives = immutable(narratives, "narratives");
            evidence = immutable(evidence, "evidence");
        }
    }

    private static <T> List<T> immutable(List<T> values, String label) {
        return List.copyOf(Objects.requireNonNull(values, label + " 不能为空"));
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空白");
        }
        return value;
    }
}
