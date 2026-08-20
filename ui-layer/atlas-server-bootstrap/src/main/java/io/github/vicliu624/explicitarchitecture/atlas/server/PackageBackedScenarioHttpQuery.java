package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultConfigurationScenarioResolver;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioResolution;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasScenarioHttpQuery;

import java.util.Objects;
import java.util.Optional;

/** 从持久化 Package 查找场景，再交给应用层的确定性场景解析器；不读取服务器进程的环境变量。 */
final class PackageBackedScenarioHttpQuery implements AtlasScenarioHttpQuery {

    private final AtlasPackageQueryUseCase packageQuery;
    private final DefaultConfigurationScenarioResolver resolver;

    PackageBackedScenarioHttpQuery(AtlasPackageQueryUseCase packageQuery) {
        this.packageQuery = Objects.requireNonNull(packageQuery, "packageQuery 不能为空");
        this.resolver = new DefaultConfigurationScenarioResolver();
    }

    @Override
    public Optional<ScenarioResolution> resolve(AtlasId packageId, AtlasId scenarioId) {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Objects.requireNonNull(scenarioId, "scenarioId 不能为空");
        return packageQuery.findById(packageId).flatMap(atlasPackage -> atlasPackage.designAtlas().nodes().stream()
                .filter(ConfigurationScenario.class::isInstance).map(ConfigurationScenario.class::cast)
                .filter(scenario -> scenario.id().equals(scenarioId)).findFirst()
                .map(scenario -> resolver.resolve(atlasPackage, scenario)));
    }
}
