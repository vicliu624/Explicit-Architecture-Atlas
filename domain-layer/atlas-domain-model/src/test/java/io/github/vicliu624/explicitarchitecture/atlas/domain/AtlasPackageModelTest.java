package io.github.vicliu624.explicitarchitecture.atlas.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtlasPackageModelTest {

    @Test
    void factRelationsMustPointToFactsInTheSameBundle() {
        SourceFact source = fact("fact:source");
        FactRelation relation = new FactRelation(
                AtlasId.of("fact-relation:missing-target"),
                RelationKind.CALLS,
                source.id(),
                AtlasId.of("fact:missing"),
                Map.of(),
                List.of(evidence("evidence:fact-relation")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new FactBundle(List.of(source), List.of(relation)));

        assertEquals("事实关系引用了不存在的事实: fact-relation:missing-target", exception.getMessage());
    }

    @Test
    void packageKeepsCoverageForUnavailableAnalyzersInsteadOfErasingIt() {
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest(
                        "1.0",
                        AtlasId.of("package:sample"),
                        AtlasId.of("project:sample"),
                        "revision:sample",
                        Instant.parse("2026-07-31T00:00:00Z"),
                        List.of(new CoverageReport(
                                "analyzer:axon",
                                CapabilityStatus.NOT_IMPLEMENTED,
                                "project://",
                                "Axon adapter is not installed."))),
                AtlasGraph.empty(),
                FactBundle.empty(),
                IntentBundle.empty(),
                ContentBundle.empty(),
                Optional.empty(),
                List.of());

        assertEquals(CapabilityStatus.NOT_IMPLEMENTED, atlasPackage.manifest().coverage().getFirst().status());
    }

    @Test
    void externalBoundariesAreFirstClassGraphNodes() {
        ExternalBoundary boundary = new ExternalBoundary(
                AssetMetadata.of(AtlasId.of("boundary:payment-gateway"), "支付网关", List.of(evidence("evidence:boundary"))),
                BoundaryKind.REMOTE_SERVICE,
                "处理本系统范围外的支付请求");

        AtlasGraph graph = new AtlasGraph(List.of(boundary), List.of());

        assertEquals(AssetKind.EXTERNAL_BOUNDARY, graph.nodes().getFirst().kind());
    }

    @Test
    void duplicateCoverageDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasPackageManifest(
                "1.0",
                AtlasId.of("package:sample"),
                AtlasId.of("project:sample"),
                "revision:sample",
                Instant.parse("2026-07-31T00:00:00Z"),
                List.of(
                        new CoverageReport("analyzer:jvm", CapabilityStatus.COMPLETE, "project://", "first"),
                        new CoverageReport("analyzer:jvm", CapabilityStatus.PARTIAL, "project://", "second"))));
    }

    private static SourceFact fact(String id) {
        return new SourceFact(
                AtlasId.of(id),
                "analyzer:test",
                FactKind.SOURCE_METHOD,
                id,
                Map.of(),
                List.of(evidence("evidence:" + id.substring("fact:".length()))));
    }

    private static Evidence evidence(String id) {
        return new Evidence(
                AtlasId.of(id),
                EvidenceOrigin.SOURCE_CODE,
                EvidenceClaim.DERIVED,
                EvidenceStatus.VERIFIED,
                new SourceLocator("project://src/main/java/Sample.java", "sample.Sample", SourceRange.unknown()),
                "revision:sample",
                EvidenceVisibility.EXPERT_ONLY,
                "analyzer:test",
                "test evidence");
    }
}
