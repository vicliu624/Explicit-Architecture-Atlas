package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

/** 用于接入 Axon 运行时观测流的端口。 */
@FunctionalInterface
public interface AxonRuntimeObservationReader {

    AxonRuntimeObservationBatch read();
}
