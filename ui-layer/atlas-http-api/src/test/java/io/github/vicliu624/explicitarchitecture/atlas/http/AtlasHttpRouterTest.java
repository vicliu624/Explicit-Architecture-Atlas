package io.github.vicliu624.explicitarchitecture.atlas.http;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasAssetReference;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasLocationView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRoleViewQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasViewSummary;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.BusinessAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.ExpertAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.OperationsAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeViewAvailability;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioComponentStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInvocationResolution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInvocationStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioResolution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证无框架路由只返回角色化 JSON，而非原始 Package。 */
class AtlasHttpRouterTest {

    @Test
    void servesRoleViewsAndRejectsDisabledSourceOrUnsupportedMethods() {
        AtlasHttpRouter router = new AtlasHttpRouter(new StubViews());

        AtlasHttpResponse business = router.route("GET", "/api/v1/packages/package%3Atest/business");
        assertEquals(200, business.status());
        assertTrue(business.body().contains("\"events\""));
        assertTrue(business.body().contains("\"业务事实\""));
        assertFalse(business.body().contains("designAtlas"));

        AtlasHttpResponse operations = router.route("GET", "/api/v1/packages/package%3Atest/operations");
        assertEquals(200, operations.status());
        assertTrue(operations.body().contains("\"runtimeAvailability\":\"NOT_CAPTURED\""));

        AtlasHttpResponse sourceDisabled = router.route("GET", "/api/v1/packages/package%3Atest/assets/event%3Atest/source");
        assertEquals(409, sourceDisabled.status());
        assertTrue(sourceDisabled.body().contains("EXPERT_SOURCE_DISABLED"));

        AtlasHttpResponse method = router.route("POST", "/api/v1/packages/package%3Atest/business");
        assertEquals(405, method.status());
    }

    @Test
    void servesSourceOnlyThroughExplicitAssetIdentity() {
        AtlasHttpRouter router = new AtlasHttpRouter(new StubViews(), (packageId, assetId) -> Optional.of(new ExpertAtlasView.SourceFile(
                assetId.value(), "example.OrderEndpoint#create()", location(), "example/OrderEndpoint.java", "final class OrderEndpoint { }")));

        AtlasHttpResponse source = router.route("GET", "/api/v1/packages/package%3Atest/assets/endpoint%3Atest/source");
        assertEquals(200, source.status());
        assertTrue(source.body().contains("example/OrderEndpoint.java"));
        assertTrue(source.body().contains("final class OrderEndpoint"));
        assertFalse(source.body().contains("C:\\\\"));
    }

    @Test
    void resolvesOnlyScenarioAlreadyDeclaredInThePackage() {
        AtlasHttpRouter router = new AtlasHttpRouter(
                new StubViews(),
                Optional.empty(),
                (packageId, scenarioId) -> packageId.value().equals("package:test") && scenarioId.value().equals("scenario:prod")
                        ? Optional.of(scenarioResolution()) : Optional.empty());

        AtlasHttpResponse response = router.route("GET", "/api/v1/packages/package%3Atest/scenarios/scenario%3Aprod");
        AtlasHttpResponse missing = router.route("GET", "/api/v1/packages/package%3Atest/scenarios/scenario%3Aunknown");

        assertEquals(200, response.status());
        assertTrue(response.body().contains("scenario:prod"));
        assertTrue(response.body().contains("ACTIVE"));
        assertTrue(response.body().contains("invocations"));
        assertTrue(response.body().contains("RESOLVED"));
        assertTrue(response.body().contains("code:remote-charge"));
        assertTrue(response.body().contains("SCENARIO_WARNING"));
        assertEquals(404, missing.status());
        assertTrue(missing.body().contains("SCENARIO_NOT_FOUND"));
    }

    @Test
    void servesACompleteEndpointTraceOnlyOnDemand() {
        AtlasHttpRouter router = new AtlasHttpRouter(new StubViews());

        AtlasHttpResponse trace = router.route("GET", "/api/v1/packages/package%3Atest/endpoints/endpoint%3Atest/trace");
        AtlasHttpResponse missing = router.route("GET", "/api/v1/packages/package%3Atest/endpoints/endpoint%3Amissing/trace");

        assertEquals(200, trace.status());
        assertTrue(trace.body().contains("executionGraph"));
        assertTrue(trace.body().contains("HANDLES"));
        assertEquals(404, missing.status());
        assertTrue(missing.body().contains("ENDPOINT_TRACE_NOT_FOUND"));
    }

    @Test
    void servesACompleteEndpointTraceByStableJavaHandlerWithoutLoadingTheExpertIndex() {
        AtlasHttpRouter router = new AtlasHttpRouter(new StubViews());

        AtlasHttpResponse trace = router.route("GET", "/api/v1/packages/package%3Atest/handlers/example.OrderController%23create()/trace");

        assertEquals(200, trace.status());
        assertTrue(trace.body().contains("CreateOrderCommand"));
    }

    private static ScenarioResolution scenarioResolution() {
        Evidence evidence = new Evidence(
                AtlasId.of("evidence:scenario-http"), EvidenceOrigin.WORKSPACE_CONFIGURATION, EvidenceClaim.DECLARED,
                EvidenceStatus.VERIFIED, new SourceLocator("generated://atlas/scenario", "scenario:prod", SourceRange.unknown()),
                "test", EvidenceVisibility.EXPERT_ONLY, "test", "test scenario");
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:prod"), "production", Map.of(), List.of(evidence)), List.of("prod"), Map.of(), List.of());
        return new ScenarioResolution(scenario,
                List.of(new ScenarioComponentStatus(AtlasId.of("component:gateway"), ConditionStatus.ACTIVE, "条件满足")),
                List.of(),
                List.of(new ScenarioInvocationResolution(
                        AtlasId.of("code:caller"), AtlasId.of("code:gateway-charge"), ScenarioInvocationStatus.RESOLVED,
                        List.of(AtlasId.of("injection:gateway")), List.of(AtlasId.of("component:gateway")),
                        List.of(AtlasId.of("component:gateway")), List.of(AtlasId.of("code:remote-charge")), List.of(),
                        List.of("场景选择远程实现"))),
                List.of(new AtlasDiagnostic(
                        AtlasId.of("diagnostic:scenario-http"), DiagnosticSeverity.WARNING, "SCENARIO_WARNING", "验证 Optional 序列化",
                        Optional.empty())));
    }

    private static AtlasLocationView location() {
        return new AtlasLocationView("project://sample/src/main/java/example/OrderEndpoint.java", "example.OrderEndpoint#create()", 1, 1, 1, 30);
    }

    private static final class StubViews implements AtlasRoleViewQueryUseCase {

        private final AtlasViewSummary summary = new AtlasViewSummary(
                "package:test", "project:test", "revision", "2026-07-31T00:00:00Z", RuntimeViewAvailability.NOT_CAPTURED,
                "", 1, 0, 0, List.of());

        @Override
        public Optional<AtlasViewSummary> findOverview(AtlasId packageId) {
            return exists(packageId) ? Optional.of(summary) : Optional.empty();
        }

        @Override
        public Optional<BusinessAtlasView> findBusinessView(AtlasId packageId) {
            return exists(packageId) ? Optional.of(new BusinessAtlasView(
                    summary,
                    List.of(), List.of(),
                    List.of(new BusinessAtlasView.Event(
                            new AtlasAssetReference("event:test", "业务事实", "DOMAIN_EVENT", "订单已创建"),
                            "订单已创建", List.of(), List.of(), List.of(), List.of(), List.of())),
                    List.of(), List.of(), List.of())) : Optional.empty();
        }

        @Override
        public Optional<OperationsAtlasView> findOperationsView(AtlasId packageId) {
            return exists(packageId) ? Optional.of(new OperationsAtlasView(
                    summary, RuntimeViewAvailability.NOT_CAPTURED, "尚未采集", List.of(), List.of())) : Optional.empty();
        }

        @Override
        public Optional<ExpertAtlasView> findExpertView(AtlasId packageId) {
            return exists(packageId) ? Optional.of(new ExpertAtlasView(summary, List.of(), List.of(), List.of(), List.of())) : Optional.empty();
        }

        @Override
        public Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTrace(AtlasId packageId, AtlasId endpointId) {
            if (!exists(packageId) || !endpointId.value().equals("endpoint:test")) {
                return Optional.empty();
            }
            AtlasAssetReference endpoint = new AtlasAssetReference("endpoint:test", "Create order", "ENDPOINT_OPERATION", "test");
            AtlasAssetReference command = new AtlasAssetReference("command:test", "CreateOrderCommand", "CODE_ELEMENT", "test");
            return Optional.of(new ExpertAtlasView.EndpointTrace(endpoint, List.of("POST"), List.of("/orders"), List.of(),
                    new ExpertAtlasView.ExecutionGraph(
                            List.of(new ExpertAtlasView.TraceNode(endpoint, "", location()),
                                    new ExpertAtlasView.TraceNode(command, "example.CreateOrderHandler#handle()", location())),
                            List.of(new ExpertAtlasView.TraceRelation(
                                    new io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRelationView(
                                            "relation:test:handles", "HANDLES", command, endpoint, List.of(), List.of()), "REVERSE"))),
                    List.of()));
        }

        @Override
        public Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTraceByHandler(AtlasId packageId, String handlerQualifiedName) {
            return handlerQualifiedName.equals("example.OrderController#create()")
                    ? findExpertEndpointTrace(packageId, AtlasId.of("endpoint:test")) : Optional.empty();
        }

        @Override
        public Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId) {
            return Optional.empty();
        }

        private static boolean exists(AtlasId packageId) {
            return packageId.value().equals("package:test");
        }
    }
}
