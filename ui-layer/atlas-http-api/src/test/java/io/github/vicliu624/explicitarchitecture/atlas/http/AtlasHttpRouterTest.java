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
import org.junit.jupiter.api.Test;

import java.util.List;
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
        public Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId) {
            return Optional.empty();
        }

        private static boolean exists(AtlasId packageId) {
            return packageId.value().equals("package:test");
        }
    }
}
