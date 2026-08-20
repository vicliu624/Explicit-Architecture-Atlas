package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.Objects;

/**
 * 函数、构造器或框架入口的可解释契约。
 *
 * <p>实际参数绑定、值流、调用、Bean 解析和代理链以关系节点补充；本类型保存可直接展示的稳定签名、输入、结果
 * 和失败摘要。</p>
 */
public record OperationContract(
        AssetMetadata metadata,
        OperationKind operationKind,
        String javaSignature,
        SourceLocator locator,
        List<OperationInput> inputs,
        List<OperationOutcome> outcomes,
        List<OperationFailure> failures) implements ArchitectureAsset {

    public OperationContract {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Objects.requireNonNull(operationKind, "operationKind 不能为空");
        Objects.requireNonNull(javaSignature, "javaSignature 不能为空");
        javaSignature = javaSignature.trim();
        if (javaSignature.isEmpty()) {
            throw new IllegalArgumentException("javaSignature 不能为空白");
        }
        Objects.requireNonNull(locator, "locator 不能为空");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs 不能为空"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes 不能为空"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures 不能为空"));
    }

    @Override
    public AssetKind kind() {
        return AssetKind.OPERATION_CONTRACT;
    }
}
