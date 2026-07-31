package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Map;
import java.util.Objects;

/** 开发者通过 Atlas 注解明确声明的架构/业务意图。 */
public record IntentDeclaration(
        AtlasId id,
        String annotationType,
        AssetKind assetKind,
        IntentTarget target,
        Map<String, AtlasValue> attributes,
        Evidence evidence) {

    public IntentDeclaration {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(annotationType, "annotationType 不能为空");
        annotationType = annotationType.trim();
        if (annotationType.isEmpty()) {
            throw new IllegalArgumentException("annotationType 不能为空白");
        }
        Objects.requireNonNull(assetKind, "assetKind 不能为空");
        Objects.requireNonNull(target, "target 不能为空");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        Objects.requireNonNull(evidence, "evidence 不能为空");
        if (evidence.claim() != EvidenceClaim.DECLARED) {
            throw new IllegalArgumentException("意图声明证据必须标记为 DECLARED");
        }
    }
}
