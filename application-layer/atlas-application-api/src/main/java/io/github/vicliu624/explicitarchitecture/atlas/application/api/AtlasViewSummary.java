package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.List;
import java.util.Objects;

/** 每一种角色视图共享的 Package 身份、来源版本和覆盖范围摘要。 */
public record AtlasViewSummary(
        String packageId,
        String projectId,
        String sourceRevision,
        String generatedAt,
        RuntimeViewAvailability runtimeAvailability,
        String runtimeCapturedAt,
        int assetCount,
        int relationCount,
        int diagnosticCount,
        List<Coverage> coverage) {

    public AtlasViewSummary {
        packageId = required(packageId, "packageId");
        projectId = required(projectId, "projectId");
        sourceRevision = required(sourceRevision, "sourceRevision");
        generatedAt = required(generatedAt, "generatedAt");
        Objects.requireNonNull(runtimeAvailability, "runtimeAvailability 不能为空");
        runtimeCapturedAt = runtimeCapturedAt == null ? "" : runtimeCapturedAt.trim();
        if (assetCount < 0 || relationCount < 0 || diagnosticCount < 0) {
            throw new IllegalArgumentException("Atlas 视图计数不能为负数");
        }
        coverage = List.copyOf(Objects.requireNonNull(coverage, "coverage 不能为空"));
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空白");
        }
        return value;
    }

    /** 某个分析器对当前 Package 的能力覆盖声明。 */
    public record Coverage(String analyzerId, String status, String scopeUri, String summary) {
        public Coverage {
            analyzerId = required(analyzerId, "analyzerId");
            status = required(status, "status");
            scopeUri = scopeUri == null ? "" : scopeUri.trim();
            summary = summary == null ? "" : summary.trim();
        }
    }
}
