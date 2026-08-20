package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/**
 * 规范化的组件或控制流激活条件。
 *
 * <p>{@code canonicalForm} 用于跨扫描稳定比较；{@code terms} 保留 Profile、属性、Classpath、Bean 等条件的
 * 可查询结构。复杂自定义条件可使用 {@link ConditionStatus#UNRESOLVED}，但不得伪装为已激活。</p>
 */
public record ConditionExpression(String canonicalForm, ConditionStatus status, List<ConditionTerm> terms) {

    public ConditionExpression {
        Objects.requireNonNull(canonicalForm, "canonicalForm 不能为空");
        canonicalForm = canonicalForm.trim();
        if (canonicalForm.isEmpty()) {
            throw new IllegalArgumentException("canonicalForm 不能为空白");
        }
        Objects.requireNonNull(status, "status 不能为空");
        terms = List.copyOf(Objects.requireNonNull(terms, "terms 不能为空"));
    }

    public static ConditionExpression always() {
        return new ConditionExpression("true", ConditionStatus.ACTIVE, List.of());
    }
}
