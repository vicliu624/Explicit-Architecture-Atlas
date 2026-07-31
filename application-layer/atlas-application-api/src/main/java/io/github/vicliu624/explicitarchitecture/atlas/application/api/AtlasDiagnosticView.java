package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 面向业务化视图归一后的 Atlas 诊断；定位符为空时使用空字符串和零范围。 */
public record AtlasDiagnosticView(String severity, String code, String message, AtlasLocationView location) {

    public AtlasDiagnosticView {
        severity = required(severity, "severity");
        code = required(code, "code");
        message = required(message, "message");
        Objects.requireNonNull(location, "location 不能为空");
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空白");
        }
        return value;
    }
}
