package io.github.vicliu624.explicitarchitecture.atlas.http;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioResolution;

import java.util.Optional;

/**
 * 对已保存命名场景的受控读取边界。
 *
 * <p>HTTP 客户端只能引用 Package 中稳定的 scenario ID，不能提交任意属性、Profile 或文件路径覆盖服务端环境。</p>
 */
@FunctionalInterface
public interface AtlasScenarioHttpQuery {

    Optional<ScenarioResolution> resolve(AtlasId packageId, AtlasId scenarioId);
}
