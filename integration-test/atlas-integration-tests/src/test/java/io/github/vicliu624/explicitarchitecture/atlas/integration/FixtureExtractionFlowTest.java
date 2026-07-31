package io.github.vicliu624.explicitarchitecture.atlas.integration;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JvmSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.maven.MavenSourceFactsAnalyzer;
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

/** 验证真实源文件、编译期意图、归一化器和持久化 Package 的第一条完整竖向链路。 */
class FixtureExtractionFlowTest {

    @TempDir
    Path workspace;

    @Test
    void extractsConstraintEvidenceAndEndpointCallChainFromTheStrictFixture() {
        Path fixtureRoot = Path.of("..", "fixture-projects", "strict-explicit-java-sample").toAbsolutePath().normalize();
        Path manifest = fixtureRoot.resolve("target/classes/META-INF/explicit-architecture-atlas/atlas-intent.json");
        assertTrue(Files.isRegularFile(manifest), "fixture 编译应生成 Atlas Intent Manifest");

        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace));
        AtlasPackage atlasPackage = new DefaultAtlasBuildService(
                repository,
                List.of(new MavenSourceFactsAnalyzer(), new JvmSourceFactsAnalyzer()),
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
        assertEquals(atlasPackage.manifest().packageId(), repository.findById(atlasPackage.manifest().packageId()).orElseThrow().manifest().packageId());
    }
}
