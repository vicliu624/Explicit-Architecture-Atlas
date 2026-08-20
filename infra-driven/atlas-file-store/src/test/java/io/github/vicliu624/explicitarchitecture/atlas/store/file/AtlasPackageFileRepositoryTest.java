package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BeanDefinitionOverridePolicy;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasPackageFileRepositoryTest {

    @TempDir
    Path workspace;

    @Test
    void savesAndReloadsTheCompleteTypedPackageUsingAWindowsSafeFilename() {
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace));
        AtlasId packageId = AtlasId.of("package:fixture:001");
        AtlasPackage source = new AtlasPackage(
                new AtlasPackageManifest("1.0", packageId, AtlasId.of("project:fixture"), "revision:test",
                        Instant.parse("2026-07-31T00:00:00Z"), List.of()),
                new AtlasGraph(List.of(new BusinessConcept(
                        AssetMetadata.of(AtlasId.of("sample-business"), "样例业务", List.of(evidence())),
                        "验证 JSON Package 读写")), List.of()),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());

        repository.save(source);
        AtlasPackage restored = repository.findById(packageId).orElseThrow();

        assertEquals(packageId, restored.manifest().packageId());
        assertEquals("样例业务", restored.designAtlas().nodes().getFirst().name());
        assertTrue(repository.fileOf(packageId).getFileName().toString().endsWith(".atlas.json"));
        assertTrue(!repository.fileOf(packageId).getFileName().toString().contains(":"));
    }

    @Test
    void redactedScenarioValuesAreRemovedBeforePackageSerialization() throws Exception {
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace));
        AtlasId packageId = AtlasId.of("package:scenario-redaction");
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:production"), "production", Map.of(), List.of(evidence())),
                List.of("prod"),
                Map.of("payment.api-token", new ScenarioProperty("private-prod.yml", "must-not-appear", true)),
                List.of("com.example.PaymentClient"),
                new ApplicationContextScope("context:app", List.of("example.App"), List.of("app-backend"), List.of("example"),
                        BeanDefinitionOverridePolicy.ALLOW, Map.of("paymentGateway", "example.RemoteGateway")));
        AtlasPackage source = new AtlasPackage(
                new AtlasPackageManifest("1.0", packageId, AtlasId.of("project:fixture"), "revision:test", Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(scenario), List.of()), FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());

        repository.save(source);
        String json = Files.readString(repository.fileOf(packageId));
        ConfigurationScenario restored = (ConfigurationScenario) repository.findById(packageId).orElseThrow()
                .designAtlas().nodes().getFirst();

        assertTrue(!json.contains("must-not-appear"));
        assertTrue(restored.properties().get("payment.api-token").redacted());
        assertEquals("", restored.properties().get("payment.api-token").value());
        assertEquals("context:app", restored.applicationContext().contextId());
        assertEquals("example.RemoteGateway", restored.applicationContext().explicitBeanOverrides().get("paymentGateway"));
    }

    private static Evidence evidence() {
        return new Evidence(
                AtlasId.of("evidence:fixture"), EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, EvidenceStatus.VERIFIED,
                new SourceLocator("generated://fixture/package", "fixture", SourceRange.unknown()), "revision:test",
                EvidenceVisibility.EXPERT_ONLY, "test", "fixture evidence");
    }
}
