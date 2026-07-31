package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 可点击、可回溯的事实或意图来源。 */
public record Evidence(EvidenceSource source, EvidenceStatus status, String locator, String summary) {
    public Evidence {
        Objects.requireNonNull(source, "source 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator 不能为空");
        }
    }
}
