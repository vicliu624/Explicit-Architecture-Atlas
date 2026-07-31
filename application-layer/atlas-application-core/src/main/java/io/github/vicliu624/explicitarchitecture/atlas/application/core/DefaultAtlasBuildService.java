package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageRepository;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 将多个事实贡献编译为一个可复现的 Atlas Package。 */
public final class DefaultAtlasBuildService implements AtlasBuildUseCase {

    private final AtlasPackageRepository packageRepository;
    private final List<SourceFactsAnalyzer> analyzers;
    private final DesignAtlasResolver resolver;

    public DefaultAtlasBuildService(
            AtlasPackageRepository packageRepository,
            List<SourceFactsAnalyzer> analyzers,
            DesignAtlasResolver resolver) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "packageRepository 不能为空");
        this.analyzers = List.copyOf(Objects.requireNonNull(analyzers, "analyzers 不能为空"));
        this.resolver = Objects.requireNonNull(resolver, "resolver 不能为空");
        assertUniqueAnalyzerIds(this.analyzers);
    }

    @Override
    public AtlasPackage build(AtlasBuildRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        AnalysisRequest analysisRequest = new AnalysisRequest(
                request.projectId(), request.projectRoot(), request.sourceRevision(), request.conventionProfile());
        List<AnalyzerContribution> contributions = new ArrayList<>();
        List<AtlasDiagnostic> diagnostics = new ArrayList<>();
        for (SourceFactsAnalyzer analyzer : analyzers) {
            contributions.add(runAnalyzer(analyzer, analysisRequest, diagnostics));
        }

        FactBundle facts = new FactBundle(
                contributions.stream().flatMap(contribution -> contribution.facts().stream()).toList(),
                contributions.stream().flatMap(contribution -> contribution.relations().stream()).toList());
        contributions.forEach(contribution -> diagnostics.addAll(contribution.diagnostics()));
        DesignAtlasResolution resolution = resolver.resolve(facts, request.intents(), request.content());
        diagnostics.addAll(resolution.diagnostics());

        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest(
                        "1.0",
                        request.packageId(),
                        request.projectId(),
                        request.sourceRevision(),
                        Instant.now(),
                        contributions.stream().map(AnalyzerContribution::coverage)
                                .sorted(Comparator.comparing(CoverageReport::analyzerId)).toList()),
                resolution.designAtlas(),
                facts,
                request.intents(),
                request.content(),
                Optional.empty(),
                diagnostics);
        packageRepository.save(atlasPackage);
        return atlasPackage;
    }

    private static AnalyzerContribution runAnalyzer(
            SourceFactsAnalyzer analyzer,
            AnalysisRequest request,
            List<AtlasDiagnostic> diagnostics) {
        try {
            AnalyzerContribution contribution = Objects.requireNonNull(analyzer.analyze(request), "分析器不能返回 null");
            String expectedAnalyzerId = analyzer.descriptor().id().value();
            if (!expectedAnalyzerId.equals(contribution.coverage().analyzerId())) {
                throw new IllegalStateException("分析器贡献的 coverage 身份与 descriptor 不一致: " + expectedAnalyzerId);
            }
            return contribution;
        } catch (RuntimeException exception) {
            String analyzerId = analyzer.descriptor().id().value();
            diagnostics.add(failureDiagnostic(analyzerId, exception));
            return new AnalyzerContribution(
                    new CoverageReport(analyzerId, CapabilityStatus.FAILED, request.scopeUri(), exception.getMessage()),
                    List.of(),
                    List.of(),
                    List.of());
        }
    }

    private static AtlasDiagnostic failureDiagnostic(String analyzerId, RuntimeException exception) {
        Evidence evidence = new Evidence(
                AtlasId.of("evidence:" + analyzerId + ":failure"),
                EvidenceOrigin.SOURCE_CODE,
                EvidenceClaim.DERIVED,
                EvidenceStatus.PARTIAL,
                new SourceLocator("generated://atlas/compiler", analyzerId, SourceRange.unknown()),
                "unversioned",
                EvidenceVisibility.EXPERT_ONLY,
                "atlas-compiler",
                exception.getClass().getSimpleName());
        return new AtlasDiagnostic(
                AtlasId.of("diagnostic:" + analyzerId + ":failed"),
                DiagnosticSeverity.ERROR,
                "ANALYZER_FAILED",
                "分析器 " + analyzerId + " 执行失败: " + exception.getMessage(),
                Optional.of(evidence.locator()));
    }

    private static void assertUniqueAnalyzerIds(List<SourceFactsAnalyzer> analyzers) {
        Set<AtlasId> ids = new HashSet<>();
        for (SourceFactsAnalyzer analyzer : analyzers) {
            SourceFactsAnalyzer nonNullAnalyzer = Objects.requireNonNull(analyzer, "analyzer 不能为空");
            if (!ids.add(nonNullAnalyzer.descriptor().id())) {
                throw new IllegalArgumentException("分析器身份重复: " + nonNullAnalyzer.descriptor().id());
            }
        }
    }
}
