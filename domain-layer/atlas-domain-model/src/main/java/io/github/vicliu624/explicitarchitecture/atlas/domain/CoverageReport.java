package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 一个分析器对某个范围实际覆盖到什么程度的声明。 */
public record CoverageReport(
        String analyzerId,
        CapabilityStatus status,
        String scopeUri,
        String summary) {

    public CoverageReport {
        Objects.requireNonNull(analyzerId, "analyzerId 不能为空");
        analyzerId = analyzerId.trim();
        if (analyzerId.isEmpty()) {
            throw new IllegalArgumentException("analyzerId 不能为空白");
        }
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(scopeUri, "scopeUri 不能为空");
        scopeUri = scopeUri.trim();
        if (scopeUri.isEmpty()) {
            throw new IllegalArgumentException("scopeUri 不能为空白");
        }
        summary = summary == null ? "" : summary.trim();
    }
}
