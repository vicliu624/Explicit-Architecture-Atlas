package io.github.vicliu624.explicitarchitecture.atlas.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpResponse;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpRouter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
        AtlasHttpResponse response = router.route(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath());
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
