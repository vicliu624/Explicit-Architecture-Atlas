package io.github.vicliu624.explicitarchitecture.atlas.integration;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DesignAtlasResolution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证应用层能在不依赖 Web、Spring、Axon 或文件系统的情况下编译可追溯 Package。 */
class AtlasBuildFlowTest {

    @Test
    void buildPersistsAPackageWithAnalyzerCoverageInsteadOfTreatingCoverageAsBusinessAbsence() {
        InMemoryRepository repository = new InMemoryRepository();
        SourceFactsAnalyzer analyzer = new SourceFactsAnalyzer() {
            @Override
            public AnalyzerDescriptor descriptor() {
                return new AnalyzerDescriptor(AtlasId.of("analyzer:test"), "测试分析器", "测试能力");
            }

            @Override
            public AnalyzerContribution analyze(AnalysisRequest request) {
                return new AnalyzerContribution(
                        new CoverageReport("analyzer:test", CapabilityStatus.COMPLETE, request.scopeUri(), "测试范围已完成"),
                        List.of(), List.of(), List.of());
            }
        };
        AtlasPackage atlasPackage = new DefaultAtlasBuildService(
                repository,
                List.of(analyzer),
                (facts, intents, content) -> new DesignAtlasResolution(AtlasGraph.empty(), List.of()))
                .build(new AtlasBuildRequest(
                        AtlasId.of("package:sample:working-tree"),
                        AtlasId.of("project:sample"),
                        Path.of("."),
                        "working-tree",
                        ConventionProfile.empty(),
                        IntentBundle.empty(),
                        ContentBundle.empty()));

        assertEquals("package:sample:working-tree", atlasPackage.manifest().packageId().value());
        assertEquals(CapabilityStatus.COMPLETE, atlasPackage.manifest().coverage().getFirst().status());
        assertTrue(repository.findById(atlasPackage.manifest().packageId()).isPresent());
    }

    private static final class InMemoryRepository implements AtlasPackageRepository {

        private final Map<AtlasId, AtlasPackage> data = new HashMap<>();

        @Override
        public void save(AtlasPackage atlasPackage) {
            data.put(atlasPackage.manifest().packageId(), atlasPackage);
        }

        @Override
        public Optional<AtlasPackage> findById(AtlasId packageId) {
            return Optional.ofNullable(data.get(packageId));
        }
    }
}
