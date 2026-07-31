package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

    private static Evidence evidence() {
        return new Evidence(
                AtlasId.of("evidence:fixture"), EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, EvidenceStatus.VERIFIED,
                new SourceLocator("generated://fixture/package", "fixture", SourceRange.unknown()), "revision:test",
                EvidenceVisibility.EXPERT_ONLY, "test", "fixture evidence");
    }
}
