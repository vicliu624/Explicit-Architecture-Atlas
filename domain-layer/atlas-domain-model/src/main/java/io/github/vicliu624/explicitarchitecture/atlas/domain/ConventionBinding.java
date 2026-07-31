package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Map;
import java.util.Objects;

/**
 * 一条可审计的源码约定：某种可匹配的技术事实应被归类为什么。
 *
 * <p>Profile 只能指导事实发现，不能直接伪造最终业务节点；最终节点仍须由事实、显式意图和内容在
 * {@code DesignAtlasResolver} 中归一。</p>
 */
public record ConventionBinding(
        AtlasId id,
        ConventionMatcherKind matcherKind,
        String matcher,
        FactKind emittedFactKind,
        Map<String, AtlasValue> declaredAttributes,
        String summary) {

    public ConventionBinding {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(matcherKind, "matcherKind 不能为空");
        Objects.requireNonNull(matcher, "matcher 不能为空");
        matcher = matcher.trim();
        if (matcher.isEmpty()) {
            throw new IllegalArgumentException("matcher 不能为空白");
        }
        Objects.requireNonNull(emittedFactKind, "emittedFactKind 不能为空");
        declaredAttributes = Map.copyOf(Objects.requireNonNull(declaredAttributes, "declaredAttributes 不能为空"));
        summary = summary == null ? "" : summary.trim();
    }
}
