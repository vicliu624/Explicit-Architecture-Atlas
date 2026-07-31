package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 从 Axon 运行环境获得的一条观测记录。
 *
 * <p>它刻意不引入 Axon SDK 类型，使运行时采集器与 Atlas 的稳定数据契约可以分别升级。</p>
 */
public record AxonRuntimeObservation(
        String observationId,
        String eventType,
        Instant observedAt,
        Map<String, String> attributes) {

    public AxonRuntimeObservation {
        Objects.requireNonNull(observationId, "observationId 不能为空");
        Objects.requireNonNull(eventType, "eventType 不能为空");
        Objects.requireNonNull(observedAt, "observedAt 不能为空");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
    }
}
