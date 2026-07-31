package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/** 一个已经发生、不可变且可被多个上下文消费的业务事实。 */
public record DomainEvent(AssetMetadata metadata, String businessFact, List<EventField> payload) implements ArchitectureAsset {

    public DomainEvent {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(businessFact, "businessFact 不能为空");
        businessFact = businessFact.trim();
        if (businessFact.isEmpty()) {
            throw new IllegalArgumentException("businessFact 不能为空白");
        }
        payload = List.copyOf(Objects.requireNonNull(payload, "payload 不能为空"));
    }

    @Override
    public AssetKind kind() {
        return AssetKind.DOMAIN_EVENT;
    }
}
