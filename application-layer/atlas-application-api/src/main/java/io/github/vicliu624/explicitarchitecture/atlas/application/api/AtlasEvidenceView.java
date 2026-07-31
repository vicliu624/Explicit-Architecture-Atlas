package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 已按角色可见性过滤的证据摘要。 */
public record AtlasEvidenceView(
        String origin,
        String claim,
        String status,
        String visibility,
        AtlasLocationView location,
        String sourceRevision,
        String analyzerId,
        String summary) {

    public AtlasEvidenceView {
        origin = required(origin, "origin");
        claim = required(claim, "claim");
        status = required(status, "status");
        visibility = required(visibility, "visibility");
        Objects.requireNonNull(location, "location 不能为空");
        sourceRevision = required(sourceRevision, "sourceRevision");
        analyzerId = required(analyzerId, "analyzerId");
        summary = summary == null ? "" : summary.trim();
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
