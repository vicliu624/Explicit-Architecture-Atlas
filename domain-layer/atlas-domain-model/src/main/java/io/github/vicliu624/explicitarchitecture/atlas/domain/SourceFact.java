package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分析器直接发现的技术事实。
 *
 * <p>Fact 不等于最终业务节点；多个 Fact、注解意图和内容说明可以共同归一为一个 Atlas 节点或关系。</p>
 */
public record SourceFact(
        AtlasId id,
        String analyzerId,
        FactKind kind,
        String subject,
        Map<String, AtlasValue> attributes,
        List<Evidence> evidence) {

    public SourceFact {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(analyzerId, "analyzerId 不能为空");
        analyzerId = analyzerId.trim();
        if (analyzerId.isEmpty()) {
            throw new IllegalArgumentException("analyzerId 不能为空白");
        }
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(subject, "subject 不能为空");
        subject = subject.trim();
        if (subject.isEmpty()) {
            throw new IllegalArgumentException("subject 不能为空白");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("技术事实至少需要一项证据");
        }
    }
}
