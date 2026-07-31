package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.Objects;

/** 不含本机路径的受控证据定位符，供专家视图跳转到源码或运行态来源。 */
public record AtlasLocationView(
        String uri,
        String symbol,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {

    public AtlasLocationView {
        Objects.requireNonNull(uri, "uri 不能为空");
        uri = uri.trim();
        if (uri.isEmpty()) {
            throw new IllegalArgumentException("uri 不能为空白");
        }
        symbol = symbol == null ? "" : symbol.trim();
        if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
            throw new IllegalArgumentException("源码范围不能为负数");
        }
    }
}
