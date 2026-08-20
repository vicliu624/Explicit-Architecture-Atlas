package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAtlasBuildServiceTest {

    @Test
    void mergesRepeatedStableDiagnosticsBeforeConstructingThePackage() {
        AtlasDiagnostic repeated = new AtlasDiagnostic(
                AtlasId.of("diagnostic:test:repeated"), DiagnosticSeverity.WARNING, "TEST_REPEAT",
                "同一源码事实被多个归一化路径报告", Optional.empty());
        AtlasPackageRepository repository = new AtlasPackageRepository() {
            @Override
            public void save(AtlasPackage atlasPackage) {
                // The asserted return value is sufficient for this in-memory test.
            }

            @Override
            public Optional<AtlasPackage> findById(AtlasId packageId) {
                return Optional.empty();
            }
        };
        DesignAtlasResolver resolver = (facts, intents, content) ->
                new DesignAtlasResolution(AtlasGraph.empty(), List.of(repeated, repeated));
        DefaultAtlasBuildService service = new DefaultAtlasBuildService(repository, List.of(), resolver);

        AtlasPackage atlasPackage = service.build(new AtlasBuildRequest(
                AtlasId.of("package:test:diagnostics"), AtlasId.of("project:test"), Path.of(".").toAbsolutePath(),
                "test", ConventionProfile.empty(), IntentBundle.empty(), ContentBundle.empty()));

        assertEquals(List.of(repeated), atlasPackage.diagnostics());
    }
}
