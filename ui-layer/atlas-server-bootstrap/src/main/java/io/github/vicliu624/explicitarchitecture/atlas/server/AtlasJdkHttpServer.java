package io.github.vicliu624.explicitarchitecture.atlas.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpResponse;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpRouter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 使用 JDK HttpServer 承载 Atlas 无框架路由器的轻量本地/内网服务。 */
public final class AtlasJdkHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    private AtlasJdkHttpServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    /** 绑定指定端口；端口为 {@code 0} 时由操作系统分配空闲端口。 */
    public static AtlasJdkHttpServer start(int port, AtlasHttpRouter router) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 0 到 65535 之间");
        }
        Objects.requireNonNull(router, "router 不能为空");
        try {
            // V1 没有身份认证，故只能监听回环地址；需要内网暴露时应放在具备身份边界的网关之后。
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/", exchange -> handle(exchange, router));
            server.start();
            return new AtlasJdkHttpServer(server, executor);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Atlas HTTP 服务", exception);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static void handle(HttpExchange exchange, AtlasHttpRouter router) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            if (!applyLoopbackCors(exchange)) {
                exchange.sendResponseHeaders(403, -1);
            } else {
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
            return;
        }
        AtlasHttpResponse response = router.route(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath());
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        applyLoopbackCors(exchange);
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    /**
     * 独立 atlas-web 默认也绑定在回环地址，因此可以直接读取本地 runner；绝不把这个本机服务的 CORS 放宽为任意 Origin。
     * CORS 不是鉴权边界，真正的边界仍是回环绑定和专家源码显式开关。
     */
    private static boolean applyLoopbackCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || !isLoopbackOrigin(origin)) {
            return false;
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Accept, Content-Type");
        exchange.getResponseHeaders().set("Vary", "Origin");
        return true;
    }

    private static boolean isLoopbackOrigin(String value) {
        try {
            URI origin = URI.create(value);
            if (!Set.of("http", "https").contains(origin.getScheme())) {
                return false;
            }
            // java.net.URI 保留 IPv6 字面量的方括号，例如 "[::1]"。
            return Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
