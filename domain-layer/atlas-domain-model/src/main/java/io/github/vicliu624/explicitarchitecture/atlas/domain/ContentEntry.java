package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 业务或运维人员维护、并挂接到稳定 Atlas 资产的可读内容。 */
public record ContentEntry(
        AtlasId id,
        AtlasId assetId,
        Audience audience,
        String title,
        String explanation,
        String actionWhenViolated,
        Evidence evidence) {

    public ContentEntry {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(assetId, "assetId 不能为空");
        Objects.requireNonNull(audience, "audience 不能为空");
        Objects.requireNonNull(title, "title 不能为空");
        title = title.trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title 不能为空白");
        }
        Objects.requireNonNull(explanation, "explanation 不能为空");
        explanation = explanation.trim();
        if (explanation.isEmpty()) {
            throw new IllegalArgumentException("explanation 不能为空白");
        }
        actionWhenViolated = actionWhenViolated == null ? "" : actionWhenViolated.trim();
        Objects.requireNonNull(evidence, "evidence 不能为空");
        if (evidence.origin() != EvidenceOrigin.CONTENT_BUNDLE) {
            throw new IllegalArgumentException("内容条目证据必须来自 CONTENT_BUNDLE");
        }
    }
}
