package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次离线导出或安全采集获得的运行态信号批次。 */
public record RuntimeSignalBatch(Instant capturedAt, List<RuntimeSignal> signals) {

    public RuntimeSignalBatch {
        Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
        Objects.requireNonNull(signals, "signals 不能为空");
        Map<String, RuntimeSignal> byId = new LinkedHashMap<>();
        for (RuntimeSignal signal : signals) {
            signal = Objects.requireNonNull(signal, "运行态信号不能为空");
            if (byId.putIfAbsent(signal.signalId(), signal) != null) {
                throw new IllegalArgumentException("运行态信号身份重复: " + signal.signalId());
            }
        }
        signals = List.copyOf(byId.values().stream().sorted(Comparator.comparing(RuntimeSignal::signalId)).toList());
    }
}
