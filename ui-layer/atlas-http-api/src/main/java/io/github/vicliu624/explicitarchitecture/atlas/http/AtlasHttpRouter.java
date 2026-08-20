package io.github.vicliu624.explicitarchitecture.atlas.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRoleViewQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Atlas 角色化查询 API 的无框架路由器。
 *
 * <p>该类只接受 GET 查询，不读取请求体，不暴露 {@code AtlasPackage} 原始 JSON。JDK HttpServer、Spring Web 或其他
 * 服务器可以把请求适配为 {@link #route(String, String)}，从而共享相同的 URL、错误语义和 JSON 形状。</p>
 */
public final class AtlasHttpRouter {

    private static final String JSON = "application/json; charset=utf-8";

    private final AtlasRoleViewQueryUseCase views;
    private final Optional<AtlasExpertSourceHttpQuery> sourceQuery;
    private final Optional<AtlasScenarioHttpQuery> scenarioQuery;
    // Scenario diagnostics 使用 Optional 表示“没有源码定位”；HTTP JSON 必须将其稳定地编码为 null，不能让一次
    // 有诊断的真实工程查询在序列化阶段失败。
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

    public AtlasHttpRouter(AtlasRoleViewQueryUseCase views) {
        this(views, Optional.empty(), Optional.empty());
    }

    public AtlasHttpRouter(AtlasRoleViewQueryUseCase views, AtlasExpertSourceHttpQuery sourceQuery) {
        this(views, Optional.of(Objects.requireNonNull(sourceQuery, "sourceQuery 不能为空")), Optional.empty());
    }

    /** 同时启用受控源码和命名场景查询；空源码查询保留“专家源码未启用”的响应语义。 */
    public AtlasHttpRouter(
            AtlasRoleViewQueryUseCase views,
            Optional<AtlasExpertSourceHttpQuery> sourceQuery,
            AtlasScenarioHttpQuery scenarioQuery) {
        this(views, sourceQuery, Optional.of(Objects.requireNonNull(scenarioQuery, "scenarioQuery 不能为空")));
    }

    private AtlasHttpRouter(
            AtlasRoleViewQueryUseCase views,
            Optional<AtlasExpertSourceHttpQuery> sourceQuery,
            Optional<AtlasScenarioHttpQuery> scenarioQuery) {
        this.views = Objects.requireNonNull(views, "views 不能为空");
        this.sourceQuery = Objects.requireNonNull(sourceQuery, "sourceQuery 不能为空");
        this.scenarioQuery = Objects.requireNonNull(scenarioQuery, "scenarioQuery 不能为空");
    }

    public AtlasHttpResponse route(String method, String rawPath) {
        if (!"GET".equalsIgnoreCase(Objects.requireNonNull(method, "method 不能为空"))) {
            return error(405, "METHOD_NOT_ALLOWED", "该 API 仅支持 GET 查询。");
        }
        try {
            List<String> segments = segments(rawPath);
            if (segments.equals(List.of("health"))) {
                return json(200, new Health("UP", "1"));
            }
            if (segments.size() < 5 || !segments.get(0).equals("api") || !segments.get(1).equals("v1")
                    || !segments.get(2).equals("packages")) {
                return error(404, "ROUTE_NOT_FOUND", "未找到请求的 Atlas API 路由。");
            }
            AtlasId packageId = AtlasId.of(segments.get(3));
            String view = segments.get(4);
            if (segments.size() == 5) {
                return packageView(packageId, view);
            }
            if (segments.size() == 6 && view.equals("scenarios")) {
                if (scenarioQuery.isEmpty()) {
                    return error(409, "SCENARIO_QUERY_DISABLED", "服务未启用命名配置场景查询。");
                }
                return from(scenarioQuery.orElseThrow().resolve(packageId, AtlasId.of(segments.get(5))),
                        "SCENARIO_NOT_FOUND", "指定 Package 中没有该配置场景。");
            }
            if (segments.size() == 6 && view.equals("assets")) {
                return from(views.findExpertAssetDetail(packageId, AtlasId.of(segments.get(5))), "ASSET_NOT_FOUND", "未找到指定 Atlas 资产。");
            }
            if (segments.size() == 7 && view.equals("endpoints") && segments.get(6).equals("trace")) {
                return from(views.findExpertEndpointTrace(packageId, AtlasId.of(segments.get(5))),
                        "ENDPOINT_TRACE_NOT_FOUND", "未找到指定 HTTP 端点或其完整执行图。");
            }
            if (segments.size() == 7 && view.equals("handlers") && segments.get(6).equals("trace")) {
                return from(views.findExpertEndpointTraceByHandler(packageId, segments.get(5)),
                        "ENDPOINT_TRACE_NOT_FOUND", "未找到指定 Java handler 或其完整执行图。");
            }
            if (segments.size() == 7 && view.equals("assets") && segments.get(6).equals("source")) {
                if (sourceQuery.isEmpty()) {
                    return error(409, "EXPERT_SOURCE_DISABLED", "服务未启用受控 source JAR 查看；请由本地服务管理员显式启用。");
                }
                return from(sourceQuery.orElseThrow().findSource(packageId, AtlasId.of(segments.get(5))),
                        "SOURCE_NOT_FOUND", "指定资产没有可用的本地 source JAR 源码。");
            }
            return error(404, "ROUTE_NOT_FOUND", "未找到请求的 Atlas API 路由。");
        } catch (IllegalArgumentException invalidRequest) {
            return error(400, "INVALID_REQUEST", invalidRequest.getMessage());
        } catch (RuntimeException unexpected) {
            return error(500, "INTERNAL_ERROR", "Atlas 查询服务无法完成本次请求。");
        }
    }

    private AtlasHttpResponse packageView(AtlasId packageId, String view) {
        return switch (view) {
            case "overview" -> from(views.findOverview(packageId), "PACKAGE_NOT_FOUND", "未找到指定 Atlas Package。");
            case "business" -> from(views.findBusinessView(packageId), "PACKAGE_NOT_FOUND", "未找到指定 Atlas Package。");
            case "operations" -> from(views.findOperationsView(packageId), "PACKAGE_NOT_FOUND", "未找到指定 Atlas Package。");
            case "expert" -> from(views.findExpertView(packageId), "PACKAGE_NOT_FOUND", "未找到指定 Atlas Package。");
            default -> error(404, "VIEW_NOT_FOUND", "不支持的 Atlas 角色视图: " + view);
        };
    }

    private AtlasHttpResponse from(Optional<?> result, String missingCode, String missingMessage) {
        return result.<AtlasHttpResponse>map(value -> json(200, value)).orElseGet(() -> error(404, missingCode, missingMessage));
    }

    private AtlasHttpResponse json(int status, Object value) {
        try {
            return new AtlasHttpResponse(status, JSON, mapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Atlas HTTP 响应", exception);
        }
    }

    private AtlasHttpResponse error(int status, String code, String message) {
        return json(status, new ErrorBody(code, message));
    }

    private static List<String> segments(String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath 不能为空");
        String normalized = rawPath.strip();
        if (normalized.isEmpty() || normalized.equals("/")) {
            return List.of();
        }
        if (normalized.charAt(0) == '/') {
            normalized = normalized.substring(1);
        }
        return Arrays.stream(normalized.split("/", -1))
                .map(segment -> URLDecoder.decode(segment, StandardCharsets.UTF_8))
                .toList();
    }

    private record Health(String status, String apiVersion) {
    }

    private record ErrorBody(String code, String message) {
    }
}
