package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/**
 * 请求建立一次 Atlas 快照。
 *
 * <p>这里仅表达应用层意图；实际源码位置、Maven 坐标和运行时采集信息由具体驱动适配器解释。</p>
 */
public record AtlasBuildRequest(String projectId) {

    public AtlasBuildRequest {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("projectId 不能为空白");
        }
    }
}
