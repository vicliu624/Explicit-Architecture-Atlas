package io.github.vicliu624.explicitarchitecture.atlas.http;

import java.util.Objects;

/** 与具体 HTTP 服务器无关的 UTF-8 JSON 响应。 */
public record AtlasHttpResponse(int status, String contentType, String body) {

    public AtlasHttpResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP 状态码不合法: " + status);
        }
        contentType = Objects.requireNonNull(contentType, "contentType 不能为空").trim();
        if (contentType.isEmpty()) {
            throw new IllegalArgumentException("contentType 不能为空白");
        }
        body = Objects.requireNonNull(body, "body 不能为空");
    }
}
