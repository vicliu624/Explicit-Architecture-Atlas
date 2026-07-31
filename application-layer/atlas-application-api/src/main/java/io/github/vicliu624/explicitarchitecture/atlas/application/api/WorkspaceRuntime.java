package io.github.vicliu624.explicitarchitecture.atlas.application.api;

/** Runtime Overlay 是否允许在该 Workspace 中被采集；连接凭据不属于本对象。 */
public record WorkspaceRuntime(boolean enabled) {
}
