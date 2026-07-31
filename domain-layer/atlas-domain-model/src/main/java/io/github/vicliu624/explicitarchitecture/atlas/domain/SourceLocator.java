package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Objects;
import java.util.Set;

/**
 * 不泄露本机绝对路径的源码或配置证据定位。
 *
 * <p>实际路径由服务端根据 Workspace 将 {@code project://}、{@code archive://} 等 URI 映射为受控资源。</p>
 */
public record SourceLocator(String uri, String symbol, SourceRange range) {

    private static final Set<String> ALLOWED_SCHEMES = Set.of(
            "project://", "archive://", "maven://", "runtime://", "document://", "generated://");

    public SourceLocator {
        Objects.requireNonNull(uri, "uri 不能为空");
        uri = uri.trim();
        if (ALLOWED_SCHEMES.stream().noneMatch(uri::startsWith)) {
            throw new IllegalArgumentException("locator uri 必须使用 Atlas 受控协议");
        }
        symbol = symbol == null ? "" : symbol.trim();
        Objects.requireNonNull(range, "range 不能为空");
    }
}
