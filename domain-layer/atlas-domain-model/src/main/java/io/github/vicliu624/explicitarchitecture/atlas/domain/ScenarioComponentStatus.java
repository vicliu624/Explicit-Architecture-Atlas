package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 一个组件定义在指定配置场景中的解析状态及理由。 */
public record ScenarioComponentStatus(AtlasId componentId, ConditionStatus status, String reason) {

    public ScenarioComponentStatus {
        Objects.requireNonNull(componentId, "componentId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        reason = reason == null ? "" : reason.trim();
    }
}
