package io.github.vicliu624.explicitarchitecture.atlas.application.api;

/** 将受控运行态输入与静态设计图进行确定性关联的应用用例。 */
@FunctionalInterface
public interface RuntimeOverlayCompileUseCase {

    RuntimeOverlayCompileResult compile(RuntimeOverlayCompileRequest request);
}
