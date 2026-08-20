package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 场景中一个有效配置值的来源；敏感值以红acted标记保存而不是明文。 */
public record ScenarioProperty(String provenance, String value, boolean redacted) {

    public ScenarioProperty {
        Objects.requireNonNull(provenance, "provenance 不能为空");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance 不能为空白");
        }
        value = value == null ? "" : value.trim();
        if (redacted) {
            value = "";
        }
    }
}
