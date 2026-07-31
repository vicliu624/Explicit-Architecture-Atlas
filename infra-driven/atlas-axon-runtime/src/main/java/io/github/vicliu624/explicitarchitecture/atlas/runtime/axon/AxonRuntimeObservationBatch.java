package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次 Axon 运行态离线导出；不包含数据库连接、认证信息或事件原始 payload。 */
public record AxonRuntimeObservationBatch(Instant capturedAt, List<AxonRuntimeObservation> observations) {

    public AxonRuntimeObservationBatch {
        Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
        Objects.requireNonNull(observations, "observations 不能为空");
        Map<String, AxonRuntimeObservation> byId = new LinkedHashMap<>();
        for (AxonRuntimeObservation observation : observations) {
            observation = Objects.requireNonNull(observation, "观测不能为空");
            if (byId.putIfAbsent(observation.observationId(), observation) != null) {
                throw new IllegalArgumentException("Axon 观测身份重复: " + observation.observationId());
            }
        }
        observations = List.copyOf(byId.values().stream()
                .sorted(Comparator.comparing(AxonRuntimeObservation::observationId))
                .toList());
    }
}
