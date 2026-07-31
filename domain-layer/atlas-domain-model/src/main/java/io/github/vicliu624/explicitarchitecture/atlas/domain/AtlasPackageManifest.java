package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atlas Package 的身份、可复现来源和能力覆盖声明。 */
public record AtlasPackageManifest(
        String schemaVersion,
        AtlasId packageId,
        AtlasId projectId,
        String sourceRevision,
        Instant generatedAt,
        List<CoverageReport> coverage) {

    public AtlasPackageManifest {
        Objects.requireNonNull(schemaVersion, "schemaVersion 不能为空");
        schemaVersion = schemaVersion.trim();
        if (schemaVersion.isEmpty()) {
            throw new IllegalArgumentException("schemaVersion 不能为空白");
        }
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Objects.requireNonNull(projectId, "projectId 不能为空");
        Objects.requireNonNull(sourceRevision, "sourceRevision 不能为空");
        sourceRevision = sourceRevision.trim();
        if (sourceRevision.isEmpty()) {
            throw new IllegalArgumentException("sourceRevision 不能为空白");
        }
        Objects.requireNonNull(generatedAt, "generatedAt 不能为空");
        Objects.requireNonNull(coverage, "coverage 不能为空");
        Map<String, CoverageReport> byAnalyzerId = new LinkedHashMap<>();
        for (CoverageReport report : coverage) {
            report = Objects.requireNonNull(report, "coverage 条目不能为空");
            if (byAnalyzerId.putIfAbsent(report.analyzerId(), report) != null) {
                throw new IllegalArgumentException("分析器覆盖声明重复: " + report.analyzerId());
            }
        }
        coverage = List.copyOf(byAnalyzerId.values().stream()
                .sorted(Comparator.comparing(CoverageReport::analyzerId)).toList());
    }
}
