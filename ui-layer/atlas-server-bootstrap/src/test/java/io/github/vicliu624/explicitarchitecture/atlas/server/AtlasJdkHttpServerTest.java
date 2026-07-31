package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRoleViewQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasViewSummary;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.BusinessAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.ExpertAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.OperationsAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpRouter;
import io.github.vicliu624.explicitarchitecture.atlas.source.archive.SourceArchiveReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证真实 JDK HTTP 服务及受控 source JAR 查询，不依赖任何 Web 框架。 */
class AtlasJdkHttpServerTest {

    @TempDir
    Path workspace;

    @Test
    void servesHealthOverARealHttpSocket() throws Exception {
        try (AtlasJdkHttpServer server = AtlasJdkHttpServer.start(0, new AtlasHttpRouter(new EmptyViews()))) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"UP\""));
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        }
    }

    @Test
    void readsOnlyAPackageKnownAssetFromTheDeclaredSourceArchive() throws Exception {
        AtlasPackage atlasPackage = sourcePackage();
        Path archive = workspace.resolve("source.jar");
        try (ZipOutputStream output = new ZipOutputStream(java.nio.file.Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("example/OrderProjection.java"));
            output.write("package example; final class OrderProjection { }".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        AtlasPackageQueryUseCase packages = id -> id.equals(atlasPackage.manifest().packageId()) ? Optional.of(atlasPackage) : Optional.empty();
        var query = new WorkspaceBackedExpertSourceHttpQuery(packages, new SourceArchiveReader(), List.of(archive));

        var source = query.findSource(atlasPackage.manifest().packageId(), AtlasId.of("projection:orders")).orElseThrow();
        assertEquals("example/OrderProjection.java", source.archiveEntry());
        assertTrue(source.content().contains("OrderProjection"));
        assertFalse(source.content().contains(archive.toAbsolutePath().toString()));
        assertTrue(query.findSource(atlasPackage.manifest().packageId(), AtlasId.of("missing:asset")).isEmpty());

        AtlasPackage ambiguous = ambiguousSourcePackage(atlasPackage);
        AtlasPackageQueryUseCase ambiguousPackages = id -> id.equals(ambiguous.manifest().packageId()) ? Optional.of(ambiguous) : Optional.empty();
        var ambiguousQuery = new WorkspaceBackedExpertSourceHttpQuery(ambiguousPackages, new SourceArchiveReader(), List.of(archive));
        assertTrue(ambiguousQuery.findSource(ambiguous.manifest().packageId(), AtlasId.of("projection:orders")).isEmpty());
    }

    @Test
    void composesWorkspacePackageRepositoryAndBusinessJsonOverHttp() throws Exception {
        AtlasPackage atlasPackage = sourcePackage();
        new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace)).save(atlasPackage);
        Path project = workspace.resolve("project");
        java.nio.file.Files.createDirectories(project);
        Path workspaceFile = workspace.resolve("atlas-workspace.yaml");
        java.nio.file.Files.writeString(workspaceFile, """
                schemaVersion: "1.0"
                workspaceId: "workspace:server-test"
                project:
                  id: "project:source"
                  displayName: "Server Test"
                sources:
                  projectRoot: "%s"
                  sourceArchives: []
                  intentManifests: []
                """.formatted(project.toAbsolutePath().toString().replace('\\', '/')));

        try (AtlasJdkHttpServer server = AtlasServerApplication.start(workspaceFile, 0, false)) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port()
                            + "/api/v1/packages/package%3Asource/business")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"projections\""));
            assertTrue(response.body().contains("订单投影"));
            assertFalse(response.body().contains("designAtlas"));
        }
    }

    private static AtlasPackage sourcePackage() {
        Evidence evidence = new Evidence(
                AtlasId.of("evidence:source"), EvidenceOrigin.SOURCE_CODE, EvidenceClaim.OBSERVED, EvidenceStatus.VERIFIED,
                new SourceLocator("project://sample/src/main/java/example/OrderProjection.java", "example.OrderProjection", new SourceRange(1, 1, 1, 40)),
                "test", EvidenceVisibility.EXPERT_ONLY, "test", "source fixture");
        CodeElement code = new CodeElement(
                new AssetMetadata(AtlasId.of("code:order-projection"), "OrderProjection", Map.of(), List.of(evidence)),
                CodeElementKind.TYPE, "example.OrderProjection", evidence.locator());
        Projection projection = new Projection(
                new AssetMetadata(AtlasId.of("projection:orders"), "订单投影", Map.of(), List.of(evidence)),
                "展示订单状态");
        AtlasRelation represents = new AtlasRelation(
                AtlasId.of("relation:represents"), RelationKind.REPRESENTS, projection.id(), code.id(), Map.of(), List.of(evidence));
        return new AtlasPackage(
                new AtlasPackageManifest("1.0", AtlasId.of("package:source"), AtlasId.of("project:source"), "test", Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(code, projection), List.of(represents)),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
    }

    private static AtlasPackage ambiguousSourcePackage(AtlasPackage sourcePackage) {
        CodeElement original = sourcePackage.designAtlas().nodes().stream()
                .filter(CodeElement.class::isInstance).map(CodeElement.class::cast).findFirst().orElseThrow();
        CodeElement duplicate = new CodeElement(
                new AssetMetadata(AtlasId.of("code:order-projection-duplicate"), "OrderProjectionDuplicate", Map.of(), original.evidence()),
                CodeElementKind.TYPE, "example.OrderProjectionDuplicate", original.locator());
        AtlasRelation secondRepresentation = new AtlasRelation(
                AtlasId.of("relation:represents-duplicate"), RelationKind.REPRESENTS, AtlasId.of("projection:orders"), duplicate.id(), Map.of(), original.evidence());
        List<ArchitectureAsset> nodes = new java.util.ArrayList<>(sourcePackage.designAtlas().nodes());
        nodes.add(duplicate);
        List<AtlasRelation> relations = new java.util.ArrayList<>(sourcePackage.designAtlas().relations());
        relations.add(secondRepresentation);
        return new AtlasPackage(
                sourcePackage.manifest(), new AtlasGraph(nodes, relations), sourcePackage.facts(), sourcePackage.intents(), sourcePackage.content(),
                sourcePackage.runtimeOverlay(), sourcePackage.diagnostics());
    }

    private static final class EmptyViews implements AtlasRoleViewQueryUseCase {

        @Override
        public Optional<AtlasViewSummary> findOverview(AtlasId packageId) {
            return Optional.empty();
        }

        @Override
        public Optional<BusinessAtlasView> findBusinessView(AtlasId packageId) {
            return Optional.empty();
        }

        @Override
        public Optional<OperationsAtlasView> findOperationsView(AtlasId packageId) {
            return Optional.empty();
        }

        @Override
        public Optional<ExpertAtlasView> findExpertView(AtlasId packageId) {
            return Optional.empty();
        }

        @Override
        public Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId) {
            return Optional.empty();
        }
    }
}
