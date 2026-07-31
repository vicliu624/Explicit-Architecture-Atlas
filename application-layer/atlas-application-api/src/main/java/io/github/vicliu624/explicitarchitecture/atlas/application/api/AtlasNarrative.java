package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 人工维护的业务说明或运维处置说明，经 Audience 过滤后供对应角色阅读。 */
public record AtlasNarrative(String title, String explanation, String actionWhenViolated) {

    public AtlasNarrative {
        Objects.requireNonNull(title, "title 不能为空");
        title = title.trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title 不能为空白");
        }
        Objects.requireNonNull(explanation, "explanation 不能为空");
        explanation = explanation.trim();
        if (explanation.isEmpty()) {
            throw new IllegalArgumentException("explanation 不能为空白");
        }
        actionWhenViolated = actionWhenViolated == null ? "" : actionWhenViolated.trim();
    }
}
