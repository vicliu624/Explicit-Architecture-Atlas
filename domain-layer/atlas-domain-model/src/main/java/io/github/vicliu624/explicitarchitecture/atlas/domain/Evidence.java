package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/**
 * 可定位、可复现、可按角色授权的事实或意图证据。
 *
 * <p>来源载体、结论性质、可信状态被分别建模，避免把“由源码推导”与“结论已验证”混为同一个枚举。</p>
 */
public record Evidence(
        AtlasId id,
        EvidenceOrigin origin,
        EvidenceClaim claim,
        EvidenceStatus status,
        SourceLocator locator,
        String sourceRevision,
        EvidenceVisibility visibility,
        String analyzerId,
        String summary) {

    public Evidence {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(origin, "origin 不能为空");
        Objects.requireNonNull(claim, "claim 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(locator, "locator 不能为空");
        Objects.requireNonNull(sourceRevision, "sourceRevision 不能为空");
        sourceRevision = sourceRevision.trim();
        if (sourceRevision.isEmpty()) {
            throw new IllegalArgumentException("sourceRevision 不能为空白");
        }
        Objects.requireNonNull(visibility, "visibility 不能为空");
        Objects.requireNonNull(analyzerId, "analyzerId 不能为空");
        analyzerId = analyzerId.trim();
        if (analyzerId.isEmpty()) {
            throw new IllegalArgumentException("analyzerId 不能为空白");
        }
        summary = summary == null ? "" : summary.trim();
    }
}
