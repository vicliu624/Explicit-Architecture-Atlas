package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignal;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignalBatch;

/** 将 Axon 专用离线导出归一为应用层可处理的通用运行态信号。 */
public final class AxonRuntimeSignalMapper {

    public RuntimeSignalBatch map(AxonRuntimeObservationBatch batch) {
        return new RuntimeSignalBatch(batch.capturedAt(), batch.observations().stream()
                .map(observation -> new RuntimeSignal(
                        observation.observationId(),
                        observation.eventType(),
                        observation.handlerType(),
                        observation.observedAt(),
                        observation.status(),
                        observation.attributes(),
                        observation.evidence()))
                .toList());
    }
}
