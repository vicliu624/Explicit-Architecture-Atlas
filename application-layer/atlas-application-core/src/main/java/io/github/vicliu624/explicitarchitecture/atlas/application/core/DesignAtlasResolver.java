package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;

/**
 * 将多源事实归一化为可渲染设计图谱的稳定端口。
 *
 * <p>技术分析器只产生 Fact；业务节点和关系的归一化只允许在此层发生。</p>
 */
@FunctionalInterface
public interface DesignAtlasResolver {

    DesignAtlasResolution resolve(FactBundle facts, IntentBundle intents, ContentBundle content);
}
