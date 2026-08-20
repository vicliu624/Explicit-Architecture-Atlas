package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeSignalProvider;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceRuntime;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignalBatch;

import java.util.Objects;

/**
 * Axon 离线观测的可选 ServiceLoader provider。
 *
 * <p>核心 CLI 只依赖 {@link RuntimeSignalProvider}；只有用户将本 artifact 放入 classpath 时才会读取 Axon 格式。
 * 该实现不要求业务应用引入 Atlas，也不把 Axon 运行时依赖泄漏到 core。</p>
 */
public final class AxonRuntimeSignalProvider implements RuntimeSignalProvider {

    private static final AtlasId ID = AtlasId.of("runtime-provider:axon");

    @Override
    public AtlasId id() {
        return ID;
    }

    @Override
    public RuntimeSignalBatch read(WorkspaceRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime 不能为空");
        return new AxonRuntimeSignalMapper().map(
                new JsonAxonRuntimeObservationReader(runtime.observationFiles()).read());
    }
}
