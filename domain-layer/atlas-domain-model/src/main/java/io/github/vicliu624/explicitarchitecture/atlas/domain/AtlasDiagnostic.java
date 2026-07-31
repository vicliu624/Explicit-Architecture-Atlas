package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;
import java.util.Optional;

/** 不能被静默忽略的缺失、冲突、降级或失败信息。 */
public record AtlasDiagnostic(
        AtlasId id,
        DiagnosticSeverity severity,
        String code,
        String message,
        Optional<SourceLocator> locator) {

    public AtlasDiagnostic {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(severity, "severity 不能为空");
        Objects.requireNonNull(code, "code 不能为空");
        code = code.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("code 不能为空白");
        }
        Objects.requireNonNull(message, "message 不能为空");
        message = message.trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("message 不能为空白");
        }
        locator = Objects.requireNonNull(locator, "locator 不能为空");
    }
}
