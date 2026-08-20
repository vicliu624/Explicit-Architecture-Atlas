package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioResolution;

/** 将 Atlas 的可能组件图按一个明确配置场景解析为有效 Bean 和调用候选。 */
public interface ConfigurationScenarioResolveUseCase {

    ScenarioResolution resolve(AtlasPackage atlasPackage, ConfigurationScenario scenario);
}
