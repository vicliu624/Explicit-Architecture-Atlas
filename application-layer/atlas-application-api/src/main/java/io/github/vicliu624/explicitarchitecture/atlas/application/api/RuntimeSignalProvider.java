package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignalBatch;

/**
 * 可选运行态框架适配器向 Atlas 核心提供脱敏信号的服务端口。
 *
 * <p>CLI/Server 只通过该接口发现运行态能力，不能直接依赖 Axon、Spring 或任意框架专用 reader。实现通过
 * {@link java.util.ServiceLoader} 位于各自 adapter artifact 中。</p>
 */
public interface RuntimeSignalProvider {

    /** provider 的稳定身份，同时用作 Workspace runtime.providers 中的选择值。 */
    AtlasId id();

    /** 从私有 Workspace 读取并归一为通用、已脱敏的运行态信号。 */
    RuntimeSignalBatch read(WorkspaceRuntime runtime);
}
