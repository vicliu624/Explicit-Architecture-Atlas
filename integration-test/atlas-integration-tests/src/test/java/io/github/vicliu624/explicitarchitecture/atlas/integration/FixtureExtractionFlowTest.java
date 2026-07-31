package io.github.vicliu624.explicitarchitecture.atlas.integration;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTargetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.source.axon.AxonSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.jpa.JpaProjectionTargetResolver;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JvmSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.maven.MavenSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.spring.SpringSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasIntentManifestReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证真实 Spring/Axon 源文件、编译期意图、归一化器和持久化 Package 的完整业务流。 */
class FixtureExtractionFlowTest {

    @TempDir
    Path workspace;

    @Test
    void extractsConstraintAndSpringAxonBusinessFlowFromTheStrictFixture() {
        Path fixtureRoot = Path.of("..", "fixture-projects", "strict-explicit-java-sample").toAbsolutePath().normalize();
        Path manifest = fixtureRoot.resolve("target/classes/META-INF/explicit-architecture-atlas/atlas-intent.json");
        assertTrue(Files.isRegularFile(manifest), "fixture 编译应生成 Atlas Intent Manifest");

        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace));
        AtlasPackage atlasPackage = new DefaultAtlasBuildService(
                repository,
                List.of(
                        new MavenSourceFactsAnalyzer(),
                        new JvmSourceFactsAnalyzer(),
                        new SpringSourceFactsAnalyzer(),
                        new AxonSourceFactsAnalyzer(),
                        new JpaProjectionTargetResolver()),
                new DefaultDesignAtlasResolver())
                .build(new AtlasBuildRequest(
                        AtlasId.of("package:fixture:integration"),
                        AtlasId.of("project:fixture"),
                        fixtureRoot,
                        "fixture-revision",
                        ConventionProfile.empty(),
                        new AtlasIntentManifestReader().read(List.of(manifest), "fixture-revision"),
                        ContentBundle.empty()));

        DomainConstraint constraint = atlasPackage.designAtlas().nodes().stream()
                .filter(DomainConstraint.class::isInstance)
                .map(DomainConstraint.class::cast)
                .filter(node -> node.id().value().equals("sample-order-id-format"))
                .findFirst()
                .orElseThrow();
        assertEquals("^[A-Z]{2}-\\d{6}$", ((AtlasValue.Text) constraint.attributes().get("expression")).value());
        assertTrue(constraint.evidence().stream().anyMatch(evidence -> evidence.origin() == EvidenceOrigin.ANNOTATION_MANIFEST));
        assertTrue(constraint.evidence().stream().anyMatch(evidence -> evidence.origin() == EvidenceOrigin.SOURCE_CODE));
        assertTrue(atlasPackage.designAtlas().relations().stream().anyMatch(relation -> relation.kind() == RelationKind.CALLS));
        assertTrue(atlasPackage.manifest().coverage().stream().allMatch(report -> report.status() == CapabilityStatus.COMPLETE));

        EndpointOperation endpoint = atlasPackage.designAtlas().nodes().stream()
                .filter(EndpointOperation.class::isInstance)
                .map(EndpointOperation.class::cast)
                .filter(node -> node.id().value().equals("sample-order-create"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("/orders/create"), values(endpoint.attributes().get("paths")));
        assertEquals(List.of("POST"), values(endpoint.attributes().get("httpMethods")));
        assertTrue(hasRelation(atlasPackage, RelationKind.DISPATCHES, endpoint.id().value(), "example.atlasfixture.CreateOrderCommand"));

        DomainEvent orderCreated = event(atlasPackage, "sample-order-created");
        assertTrue(orderCreated.payload().stream().anyMatch(field -> field.name().equals("orderId")));
        assertTrue(orderCreated.payload().stream().anyMatch(field -> field.name().equals("createdAt")));
        assertTrue(hasRelation(atlasPackage, RelationKind.PRODUCES, "sample-order-aggregate", orderCreated.id().value()));
        assertTrue(hasRelation(atlasPackage, RelationKind.CONSUMES, orderCreated.id().value(), "sample-order-summary"));
        assertTrue(hasRelation(atlasPackage, RelationKind.STARTS, orderCreated.id().value(), "sample-order-completion"));
        assertTrue(hasRelation(atlasPackage, RelationKind.DISPATCHES, "sample-order-completion", "example.atlasfixture.CompleteOrderCommand"));

        DomainEvent orderCompleted = event(atlasPackage, "sample-order-completed");
        assertTrue(hasRelation(atlasPackage, RelationKind.WAITS_FOR, "sample-order-completion", orderCompleted.id().value()));
        assertTrue(hasRelation(atlasPackage, RelationKind.COMPLETES, "sample-order-completion", orderCompleted.id().value()));

        DataTarget orderSummaryEntity = dataTarget(atlasPackage, DataTargetKind.PERSISTENCE_ENTITY, "entityType", "example.atlasfixture.OrderSummaryEntity");
        DataTarget orderSummaryTable = dataTarget(atlasPackage, DataTargetKind.DATABASE_TABLE, "tableName", "order_summary");
        DataTarget orderIdColumn = dataTarget(atlasPackage, DataTargetKind.DATABASE_COLUMN, "columnName", "order_id");
        assertEquals("query", ((AtlasValue.Text) orderSummaryTable.attributes().get("schema")).value());
        assertTrue(hasRelation(atlasPackage, RelationKind.PROJECTS_TO, "sample-order-summary", orderSummaryEntity.id().value()));
        assertTrue(hasRelation(atlasPackage, RelationKind.WRITES_TO, "sample-order-summary", orderSummaryTable.id().value()));
        assertTrue(hasRelation(atlasPackage, RelationKind.WRITES_TO, orderSummaryEntity.id().value(), orderSummaryTable.id().value()));
        assertTrue(hasRelation(atlasPackage, RelationKind.CONTAINS, orderSummaryTable.id().value(), orderIdColumn.id().value()));
        assertEquals(atlasPackage.manifest().packageId(), repository.findById(atlasPackage.manifest().packageId()).orElseThrow().manifest().packageId());
    }

    private static DomainEvent event(AtlasPackage atlasPackage, String id) {
        return atlasPackage.designAtlas().nodes().stream()
                .filter(DomainEvent.class::isInstance)
                .map(DomainEvent.class::cast)
                .filter(node -> node.id().value().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DataTarget dataTarget(AtlasPackage atlasPackage, DataTargetKind kind, String attribute, String value) {
        return atlasPackage.designAtlas().nodes().stream()
                .filter(DataTarget.class::isInstance)
                .map(DataTarget.class::cast)
                .filter(node -> node.targetKind() == kind)
                .filter(node -> node.attributes().get(attribute) instanceof AtlasValue.Text text && text.value().equals(value))
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasRelation(AtlasPackage atlasPackage, RelationKind kind, String sourceId, String targetQualifiedNameOrId) {
        return atlasPackage.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == kind && relation.sourceId().value().equals(sourceId))
                .anyMatch(relation -> relation.targetId().value().equals(targetQualifiedNameOrId)
                        || atlasPackage.designAtlas().nodes().stream()
                        .filter(node -> node.id().equals(relation.targetId()))
                        .filter(CodeElement.class::isInstance)
                        .map(CodeElement.class::cast)
                        .anyMatch(code -> code.qualifiedName().equals(targetQualifiedNameOrId)));
    }

    private static List<String> values(AtlasValue value) {
        return ((AtlasValue.Values) value).values().stream()
                .map(AtlasValue.Text.class::cast)
                .map(AtlasValue.Text::value)
                .toList();
    }
}
