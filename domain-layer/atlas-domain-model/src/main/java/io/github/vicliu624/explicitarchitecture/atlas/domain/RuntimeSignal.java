package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 尚未关联到 Atlas 设计资产的一条脱敏运行态输入。
 *
 * <p>技术适配器以事件类型和处理器实现类型提交此对象；应用层必须在静态图中证明两者确实构成事件消费关系，
 * 才能生成以 Atlas 资产身份为主体的 {@link RuntimeObservation}。</p>
 */
public record RuntimeSignal(
        String signalId,
        String eventType,
        String handlerType,
        Instant observedAt,
        RuntimeHandlingStatus status,
        Map<String, AtlasValue> attributes,
        Evidence evidence) {

    public RuntimeSignal {
        signalId = requiredToken(signalId, "signalId");
        eventType = requiredToken(eventType, "eventType");
        handlerType = requiredToken(handlerType, "handlerType");
        Objects.requireNonNull(observedAt, "observedAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = Objects.requireNonNull(evidence, "evidence 不能为空");
        if (evidence.claim() != EvidenceClaim.OBSERVED || evidence.origin() != EvidenceOrigin.RUNTIME_SYSTEM) {
            throw new IllegalArgumentException("运行态信号必须携带 RUNTIME_SYSTEM / OBSERVED 证据");
        }
    }

    private static String requiredToken(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        value = value.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " 必须是无空白稳定文本");
        }
        return value;
    }
}
