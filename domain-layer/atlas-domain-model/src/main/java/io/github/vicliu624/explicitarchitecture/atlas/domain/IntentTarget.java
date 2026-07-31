package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;

/** 编译期意图声明关联的稳定代码元素标识。 */
public record IntentTarget(String kind, String binaryName, String memberDescriptor) {

    public IntentTarget {
        Objects.requireNonNull(kind, "kind 不能为空");
        kind = kind.trim();
        if (kind.isEmpty()) {
            throw new IllegalArgumentException("kind 不能为空白");
        }
        Objects.requireNonNull(binaryName, "binaryName 不能为空");
        binaryName = binaryName.trim();
        if (binaryName.isEmpty()) {
            throw new IllegalArgumentException("binaryName 不能为空白");
        }
        memberDescriptor = memberDescriptor == null ? "" : memberDescriptor.trim();
    }
}
