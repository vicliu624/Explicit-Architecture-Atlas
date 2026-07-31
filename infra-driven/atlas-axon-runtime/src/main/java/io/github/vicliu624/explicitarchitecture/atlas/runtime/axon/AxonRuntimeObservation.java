package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHandlingStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 从受控 Axon 运行态导出中读取的一条已脱敏处理器观测。 */
public record AxonRuntimeObservation(
        String observationId,
        String eventType,
        String handlerType,
        Instant observedAt,
        RuntimeHandlingStatus status,
        Map<String, AtlasValue> attributes,
        Evidence evidence) {

    public AxonRuntimeObservation {
        observationId = token(observationId, "observationId");
        eventType = token(eventType, "eventType");
        handlerType = token(handlerType, "handlerType");
        Objects.requireNonNull(observedAt, "observedAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        Objects.requireNonNull(evidence, "evidence 不能为空");
    }

    private static String token(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        value = value.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " 必须是无空白稳定文本");
        }
        return value;
    }
}
