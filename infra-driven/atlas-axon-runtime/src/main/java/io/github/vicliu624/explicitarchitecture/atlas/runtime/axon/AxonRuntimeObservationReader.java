package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import java.util.stream.Stream;

/** 用于接入 Axon 运行时观测流的端口。 */
@FunctionalInterface
public interface AxonRuntimeObservationReader {

    Stream<AxonRuntimeObservation> read();
}
