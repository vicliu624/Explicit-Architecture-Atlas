package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 叠加在设计 Atlas 上的可过期运行态数据。
 *
 * <p>运行态不是设计快照的事实来源，故单独作为 Overlay 版本化和查询。</p>
 */
public record RuntimeOverlay(Instant capturedAt, List<RuntimeObservation> observations) {

    public RuntimeOverlay {
        Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
        Objects.requireNonNull(observations, "observations 不能为空");
        Map<AtlasId, RuntimeObservation> byId = new LinkedHashMap<>();
        for (RuntimeObservation observation : observations) {
            observation = Objects.requireNonNull(observation, "运行态观测不能为空");
            if (byId.putIfAbsent(observation.id(), observation) != null) {
                throw new IllegalArgumentException("运行态观测身份重复: " + observation.id());
            }
        }
        observations = List.copyOf(byId.values().stream().sorted(Comparator.comparing(RuntimeObservation::id)).toList());
    }

    public static Optional<RuntimeOverlay> absent() {
        return Optional.empty();
    }
}
