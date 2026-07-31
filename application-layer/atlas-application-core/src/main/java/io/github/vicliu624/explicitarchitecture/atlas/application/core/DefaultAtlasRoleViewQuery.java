package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasAssetReference;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasDiagnosticView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasEvidenceView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasLocationView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasNarrative;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasProperty;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRelationView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasRoleViewQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasViewSummary;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.BusinessAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.ExpertAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.OperationsAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeViewAvailability;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Audience;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentEntry;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ExternalBoundary;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeObservation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 将 Package 中的类型化静态图、人工内容和 Runtime Overlay 组装为角色化读取模型。
 *
 * <p>这是唯一一处将关系、属性和证据翻译成人类可读视图的应用服务。它不再分析源码，也不接触 Axon、数据库或
 * 本地 source JAR；因此 HTTP / Web 只能消费一致、可追溯的解释结果。</p>
 */
public final class DefaultAtlasRoleViewQuery implements AtlasRoleViewQueryUseCase {

    private static final int MAX_CALL_DEPTH = 12;
    private static final int MAX_CALL_PATHS_PER_ENDPOINT = 64;
    private static final Set<String> SAFE_RUNTIME_ATTRIBUTE_KEYS = Set.of(
            "lagSeconds", "retryCount", "errorCode", "errorCategory", "processingGroup", "eventSequence", "eventId", "messageId",
            "eventType", "handlerType", "handlingStatus", "impactedDataTargetIds", "discardedAttributeCount");

    private final AtlasPackageQueryUseCase packageQuery;
    private final RuntimeOverlayHealthResolver runtimeHealthResolver = new RuntimeOverlayHealthResolver();

    public DefaultAtlasRoleViewQuery(AtlasPackageQueryUseCase packageQuery) {
        this.packageQuery = Objects.requireNonNull(packageQuery, "packageQuery 不能为空");
    }

    @Override
    public Optional<AtlasViewSummary> findOverview(AtlasId packageId) {
        return atlasPackage(packageId).map(this::summary);
    }

    @Override
    public Optional<BusinessAtlasView> findBusinessView(AtlasId packageId) {
        return atlasPackage(packageId).map(this::businessView);
    }

    @Override
    public Optional<OperationsAtlasView> findOperationsView(AtlasId packageId) {
        return atlasPackage(packageId).map(this::operationsView);
    }

    @Override
    public Optional<ExpertAtlasView> findExpertView(AtlasId packageId) {
        return atlasPackage(packageId).map(this::expertView);
    }

    @Override
    public Optional<ExpertAtlasView.AssetDetail> findExpertAssetDetail(AtlasId packageId, AtlasId assetId) {
        Objects.requireNonNull(assetId, "assetId 不能为空");
        return atlasPackage(packageId).flatMap(atlasPackage -> {
            Index index = new Index(atlasPackage);
            ArchitectureAsset asset = index.asset(assetId).orElse(null);
            return asset == null ? Optional.empty() : Optional.of(assetDetail(atlasPackage, index, asset));
        });
    }

    private Optional<AtlasPackage> atlasPackage(AtlasId packageId) {
        return packageQuery.findById(Objects.requireNonNull(packageId, "packageId 不能为空"));
    }

    private AtlasViewSummary summary(AtlasPackage atlasPackage) {
        RuntimeViewAvailability availability = atlasPackage.runtimeOverlay().isPresent()
                ? RuntimeViewAvailability.AVAILABLE : RuntimeViewAvailability.NOT_CAPTURED;
        String capturedAt = atlasPackage.runtimeOverlay().map(overlay -> overlay.capturedAt().toString()).orElse("");
        return new AtlasViewSummary(
                atlasPackage.manifest().packageId().value(),
                atlasPackage.manifest().projectId().value(),
                atlasPackage.manifest().sourceRevision(),
                atlasPackage.manifest().generatedAt().toString(),
                availability,
                capturedAt,
                atlasPackage.designAtlas().nodes().size(),
                atlasPackage.designAtlas().relations().size(),
                atlasPackage.diagnostics().size(),
                atlasPackage.manifest().coverage().stream()
                        .map(coverage -> new AtlasViewSummary.Coverage(
                                coverage.analyzerId(), coverage.status().name(), coverage.scopeUri(), coverage.summary()))
                        .toList());
    }

    private BusinessAtlasView businessView(AtlasPackage atlasPackage) {
        Index index = new Index(atlasPackage);
        return new BusinessAtlasView(
                summary(atlasPackage),
                index.assetsOf(BusinessConcept.class).stream()
                        .map(asset -> new BusinessAtlasView.Concept(reference(asset), asset.responsibility(), narratives(index, asset.id(), Audience.BUSINESS)))
                        .toList(),
                index.assetsOf(DomainConstraint.class).stream().map(asset -> new BusinessAtlasView.Rule(
                        reference(asset),
                        index.reference(asset.subjectId()),
                        asset.constraintKind().name(),
                        asset.enforcementLevel().name(),
                        asset.statement(),
                        textAttribute(asset.attributes(), "expression"),
                        narratives(index, asset.id(), Audience.BUSINESS))).toList(),
                index.assetsOf(DomainEvent.class).stream().map(event -> businessEvent(index, event)).toList(),
                index.assetsOf(Projection.class).stream().map(projection -> new BusinessAtlasView.Projection(
                        reference(projection),
                        projection.purpose(),
                        index.incoming(projection.id(), RelationKind.CONSUMES).stream()
                                .map(AtlasRelation::sourceId).map(index::reference).toList(),
                        projectionTargets(index, projection.id()),
                        narratives(index, projection.id(), Audience.BUSINESS))).toList(),
                index.assetsOf(ProcessManager.class).stream().map(process -> businessProcess(index, process)).toList(),
                index.assetsOf(EndpointOperation.class).stream().map(endpoint -> new BusinessAtlasView.Endpoint(
                        reference(endpoint),
                        endpoint.purpose(),
                        valuesAttribute(endpoint.attributes(), "httpMethods"),
                        valuesAttribute(endpoint.attributes(), "paths"),
                        index.outgoing(endpoint.id(), RelationKind.DISPATCHES).stream()
                                .map(AtlasRelation::targetId).map(index::reference).toList(),
                        narratives(index, endpoint.id(), Audience.BUSINESS))).toList());
    }

    private BusinessAtlasView.Event businessEvent(Index index, DomainEvent event) {
        List<AtlasRelation> consumerRelations = index.outgoing(event.id(), RelationKind.CONSUMES);
        return new BusinessAtlasView.Event(
                reference(event),
                event.businessFact(),
                event.payload().stream().map(field -> new BusinessAtlasView.EventField(
                        field.name(), field.role().name(), field.businessMeaning())).toList(),
                index.incoming(event.id(), RelationKind.PRODUCES).stream()
                        .map(AtlasRelation::sourceId).map(index::reference).toList(),
                consumerRelations.stream().map(relation -> index.reference(relation.targetId()))
                        .map(asset -> new BusinessAtlasView.Consumer(asset, asset.kind())).toList(),
                consumerRelations.stream().map(AtlasRelation::targetId)
                        .map(index::asset).flatMap(Optional::stream)
                        .filter(Projection.class::isInstance).map(Projection.class::cast)
                        .map(projection -> new BusinessAtlasView.ProjectionOutput(
                                reference(projection), projection.purpose(), projectionTargets(index, projection.id())))
                        .distinct().toList(),
                narratives(index, event.id(), Audience.BUSINESS));
    }

    private BusinessAtlasView.Process businessProcess(Index index, ProcessManager process) {
        return new BusinessAtlasView.Process(
                reference(process),
                process.responsibility(),
                process.completionCondition(),
                index.incoming(process.id(), RelationKind.STARTS).stream()
                        .map(AtlasRelation::sourceId).map(index::reference).toList(),
                index.outgoing(process.id(), RelationKind.WAITS_FOR).stream()
                        .map(AtlasRelation::targetId).map(index::reference).toList(),
                index.outgoing(process.id(), RelationKind.COMPLETES).stream()
                        .map(AtlasRelation::targetId).map(index::reference).toList(),
                index.outgoing(process.id(), RelationKind.DISPATCHES).stream()
                        .map(AtlasRelation::targetId).map(index::reference).toList(),
                narratives(index, process.id(), Audience.BUSINESS));
    }

    private OperationsAtlasView operationsView(AtlasPackage atlasPackage) {
        Index index = new Index(atlasPackage);
        AtlasViewSummary summary = summary(atlasPackage);
        if (atlasPackage.runtimeOverlay().isEmpty()) {
            return new OperationsAtlasView(
                    summary,
                    RuntimeViewAvailability.NOT_CAPTURED,
                    "当前 Package 尚未叠加运行态观测；没有任何资产状态可供判断，也不能把它视为成功或未知。",
                    List.of(),
                    diagnostics(atlasPackage));
        }
        List<OperationsAtlasView.AssetHealth> assets = runtimeHealthResolver.assess(
                        atlasPackage.designAtlas(), atlasPackage.runtimeOverlay().orElseThrow())
                .stream().map(health -> operationsHealth(index, health)).toList();
        return new OperationsAtlasView(
                summary,
                RuntimeViewAvailability.AVAILABLE,
                "运行态状态来自一次受控离线采集；请结合采集时间和覆盖范围解释结论。",
                assets,
                diagnostics(atlasPackage));
    }

    private OperationsAtlasView.AssetHealth operationsHealth(Index index, RuntimeAssetHealth health) {
        ArchitectureAsset asset = index.asset(health.subjectId()).orElseThrow();
        Optional<RuntimeObservation> latest = health.latestObservation();
        return new OperationsAtlasView.AssetHealth(
                reference(asset),
                health.status().name(),
                latest.map(observation -> observation.observedAt().toString()).orElse(""),
                latest.map(RuntimeObservation::observationType).orElse(""),
                latest.map(RuntimeObservation::attributes).map(DefaultAtlasRoleViewQuery::safeRuntimeProperties).orElse(List.of()),
                health.impactedDataTargetIds().stream().map(index::reference).toList(),
                health.operationalSummary(),
                health.suggestedAction(),
                narratives(index, asset.id(), Audience.OPS),
                latest.stream().map(RuntimeObservation::evidence)
                        .filter(evidence -> visibleTo(evidence, Role.OPS)).map(DefaultAtlasRoleViewQuery::evidence).toList());
    }

    private ExpertAtlasView expertView(AtlasPackage atlasPackage) {
        Index index = new Index(atlasPackage);
        return new ExpertAtlasView(
                summary(atlasPackage),
                index.assetsOf(DomainEvent.class).stream().map(event -> expertEventTrace(index, event)).toList(),
                index.assetsOf(EndpointOperation.class).stream().map(endpoint -> endpointTrace(index, endpoint)).toList(),
                index.assetsOf(CodeElement.class).stream()
                        .filter(code -> code.locator().uri().startsWith("project://"))
                        .map(code -> new ExpertAtlasView.SourceBackedAsset(reference(code), code.qualifiedName(), location(code.locator())))
                        .toList(),
                diagnostics(atlasPackage));
    }

    private ExpertAtlasView.EventTrace expertEventTrace(Index index, DomainEvent event) {
        List<AtlasRelation> consumptionRelations = index.outgoing(event.id(), RelationKind.CONSUMES);
        return new ExpertAtlasView.EventTrace(
                reference(event),
                event.businessFact(),
                relationViews(index.incoming(event.id(), RelationKind.PRODUCES), index, Role.EXPERT),
                relationViews(consumptionRelations, index, Role.EXPERT),
                consumptionRelations.stream().map(AtlasRelation::targetId).distinct()
                        .map(index::asset).flatMap(Optional::stream).filter(Projection.class::isInstance).map(Projection.class::cast)
                        .map(projection -> new ExpertAtlasView.ProjectionDestination(
                                reference(projection),
                                relationViews(index.projectTargetRelations(projection.id()), index, Role.EXPERT),
                                projectionTargets(index, projection.id())))
                        .toList());
    }

    private ExpertAtlasView.EndpointTrace endpointTrace(Index index, EndpointOperation endpoint) {
        List<CodeElement> representations = index.representedCode(endpoint.id());
        List<ExpertAtlasView.CallPath> paths = new ArrayList<>();
        for (CodeElement code : representations) {
            List<ExpertAtlasView.TraceStep> initial = List.of(
                    traceStep(endpoint, ""),
                    traceStep(code, RelationKind.REPRESENTS.name()));
            collectCallPaths(index, code.id(), initial, new LinkedHashSet<>(List.of(endpoint.id(), code.id())), paths);
        }
        List<AtlasRelation> effects = new ArrayList<>(index.outgoing(endpoint.id(), RelationKind.CALLS, RelationKind.DISPATCHES));
        representations.forEach(code -> effects.addAll(index.outgoing(code.id(), RelationKind.CALLS, RelationKind.DISPATCHES)));
        return new ExpertAtlasView.EndpointTrace(
                reference(endpoint),
                valuesAttribute(endpoint.attributes(), "httpMethods"),
                valuesAttribute(endpoint.attributes(), "paths"),
                paths.stream().distinct().limit(MAX_CALL_PATHS_PER_ENDPOINT).toList(),
                relationViews(effects, index, Role.EXPERT));
    }

    private void collectCallPaths(
            Index index,
            AtlasId current,
            List<ExpertAtlasView.TraceStep> steps,
            Set<AtlasId> visited,
            List<ExpertAtlasView.CallPath> output) {
        if (output.size() >= MAX_CALL_PATHS_PER_ENDPOINT || steps.size() >= MAX_CALL_DEPTH) {
            output.add(new ExpertAtlasView.CallPath(steps));
            return;
        }
        List<AtlasRelation> nextCalls = index.outgoing(current, RelationKind.CALLS).stream()
                .filter(relation -> !visited.contains(relation.targetId())).toList();
        if (nextCalls.isEmpty()) {
            output.add(new ExpertAtlasView.CallPath(steps));
            return;
        }
        for (AtlasRelation relation : nextCalls) {
            if (output.size() >= MAX_CALL_PATHS_PER_ENDPOINT) {
                return;
            }
            ArchitectureAsset target = index.asset(relation.targetId()).orElse(null);
            if (target == null) {
                continue;
            }
            List<ExpertAtlasView.TraceStep> extended = new ArrayList<>(steps);
            extended.add(traceStep(target, relation.kind().name()));
            Set<AtlasId> extendedVisited = new LinkedHashSet<>(visited);
            extendedVisited.add(target.id());
            collectCallPaths(index, target.id(), List.copyOf(extended), extendedVisited, output);
        }
    }

    private ExpertAtlasView.AssetDetail assetDetail(AtlasPackage atlasPackage, Index index, ArchitectureAsset asset) {
        Optional<CodeElement> source = index.codeFor(asset.id());
        return new ExpertAtlasView.AssetDetail(
                summary(atlasPackage),
                reference(asset),
                properties(asset.attributes()),
                allNarratives(index, asset.id()),
                asset.evidence().stream().filter(evidence -> visibleTo(evidence, Role.EXPERT)).map(DefaultAtlasRoleViewQuery::evidence).toList(),
                relationViews(index.incoming(asset.id()), index, Role.EXPERT),
                relationViews(index.outgoing(asset.id()), index, Role.EXPERT),
                runtimeObservations(atlasPackage, asset.id()),
                source.map(code -> new ExpertAtlasView.SourceReference(
                                code.locator().uri().startsWith("project://"), code.qualifiedName(), location(code.locator())))
                        .orElseGet(() -> new ExpertAtlasView.SourceReference(false, "", emptyLocation())));
    }

    private List<ExpertAtlasView.RuntimeObservation> runtimeObservations(AtlasPackage atlasPackage, AtlasId assetId) {
        return atlasPackage.runtimeOverlay().stream().flatMap(overlay -> overlay.observations().stream())
                .filter(observation -> observation.subjectId().equals(assetId))
                .sorted(Comparator.comparing(RuntimeObservation::observedAt).reversed().thenComparing(RuntimeObservation::id))
                .map(observation -> new ExpertAtlasView.RuntimeObservation(
                        observation.id().value(),
                        observation.observedAt().toString(),
                        observation.observationType(),
                        safeRuntimeProperties(observation.attributes()),
                        observation.evidence().visibility() == EvidenceVisibility.RESTRICTED ? List.of() : List.of(evidence(observation.evidence()))))
                .toList();
    }

    private static ExpertAtlasView.TraceStep traceStep(ArchitectureAsset asset, String relationFromPrevious) {
        if (asset instanceof CodeElement code) {
            return new ExpertAtlasView.TraceStep(reference(code), relationFromPrevious, code.qualifiedName(), location(code.locator()));
        }
        return new ExpertAtlasView.TraceStep(reference(asset), relationFromPrevious, "", emptyLocation());
    }

    private static List<AtlasAssetReference> projectionTargets(Index index, AtlasId projectionId) {
        return index.projectTargetRelations(projectionId).stream()
                .map(AtlasRelation::targetId).distinct().map(index::asset).flatMap(Optional::stream)
                .filter(DataTarget.class::isInstance).map(DefaultAtlasRoleViewQuery::reference).toList();
    }

    private static List<AtlasProperty> safeRuntimeProperties(Map<String, AtlasValue> attributes) {
        return attributes.entrySet().stream()
                .filter(entry -> SAFE_RUNTIME_ATTRIBUTE_KEYS.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AtlasProperty(entry.getKey(), render(entry.getValue())))
                .toList();
    }

    private static List<AtlasProperty> properties(Map<String, AtlasValue> attributes) {
        return attributes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new AtlasProperty(entry.getKey(), render(entry.getValue()))).toList();
    }

    private static String textAttribute(Map<String, AtlasValue> attributes, String name) {
        AtlasValue value = attributes.get(name);
        return value == null ? "" : render(value);
    }

    private static List<String> valuesAttribute(Map<String, AtlasValue> attributes, String name) {
        AtlasValue value = attributes.get(name);
        if (value instanceof AtlasValue.Values values) {
            return values.values().stream().map(DefaultAtlasRoleViewQuery::render).toList();
        }
        return value == null ? List.of() : List.of(render(value));
    }

    private static String render(AtlasValue value) {
        return switch (value) {
            case AtlasValue.Text text -> text.value();
            case AtlasValue.Decimal decimal -> decimal.value().stripTrailingZeros().toPlainString();
            case AtlasValue.Bool bool -> Boolean.toString(bool.value());
            case AtlasValue.Values values -> values.values().stream().map(DefaultAtlasRoleViewQuery::render).collect(Collectors.joining("、"));
            case AtlasValue.ObjectValue object -> object.entries().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + render(entry.getValue())).collect(Collectors.joining("，", "{", "}"));
        };
    }

    private static AtlasAssetReference reference(ArchitectureAsset asset) {
        return new AtlasAssetReference(asset.id().value(), asset.name(), asset.kind().name(), assetSummary(asset));
    }

    private static String assetSummary(ArchitectureAsset asset) {
        return switch (asset) {
            case BusinessConcept concept -> concept.responsibility();
            case DomainConstraint constraint -> constraint.statement();
            case DomainEvent event -> event.businessFact();
            case ApplicationUseCase useCase -> useCase.purpose();
            case Projection projection -> projection.purpose();
            case ProcessManager process -> process.responsibility();
            case EndpointOperation endpoint -> endpoint.purpose();
            case DataTarget target -> target.targetKind().name();
            case CodeElement code -> code.qualifiedName();
            case ExternalBoundary boundary -> boundary.responsibility();
            default -> asset.name();
        };
    }

    private static AtlasLocationView location(SourceLocator locator) {
        return new AtlasLocationView(
                locator.uri(), locator.symbol(), locator.range().startLine(), locator.range().startColumn(),
                locator.range().endLine(), locator.range().endColumn());
    }

    private static AtlasLocationView emptyLocation() {
        return new AtlasLocationView("generated://atlas/no-location", "", 0, 0, 0, 0);
    }

    private static AtlasEvidenceView evidence(Evidence evidence) {
        return new AtlasEvidenceView(
                evidence.origin().name(), evidence.claim().name(), evidence.status().name(), evidence.visibility().name(),
                location(evidence.locator()), evidence.sourceRevision(), evidence.analyzerId(), evidence.summary());
    }

    private static boolean visibleTo(Evidence evidence, Role role) {
        return switch (role) {
            case BUSINESS -> evidence.visibility() == EvidenceVisibility.BUSINESS_SAFE;
            case OPS -> evidence.visibility() == EvidenceVisibility.BUSINESS_SAFE || evidence.visibility() == EvidenceVisibility.OPS_SAFE;
            case EXPERT -> evidence.visibility() != EvidenceVisibility.RESTRICTED;
        };
    }

    private static List<AtlasRelationView> relationViews(Collection<AtlasRelation> relations, Index index, Role role) {
        return relations.stream()
                .filter(relation -> relation.evidence().stream().anyMatch(evidence -> visibleTo(evidence, role)))
                .sorted(Comparator.comparing(AtlasRelation::id))
                .map(relation -> new AtlasRelationView(
                        relation.id().value(), relation.kind().name(), index.reference(relation.sourceId()), index.reference(relation.targetId()),
                        properties(relation.attributes()),
                        relation.evidence().stream().filter(evidence -> visibleTo(evidence, role)).map(DefaultAtlasRoleViewQuery::evidence).toList()))
                .toList();
    }

    private static List<AtlasNarrative> narratives(Index index, AtlasId assetId, Audience audience) {
        return index.contentFor(assetId).stream().filter(entry -> entry.audience() == audience)
                .map(DefaultAtlasRoleViewQuery::narrative).toList();
    }

    private static List<AtlasNarrative> allNarratives(Index index, AtlasId assetId) {
        return index.contentFor(assetId).stream().map(DefaultAtlasRoleViewQuery::narrative).toList();
    }

    private static AtlasNarrative narrative(ContentEntry entry) {
        return new AtlasNarrative(entry.title(), entry.explanation(), entry.actionWhenViolated());
    }

    private static List<AtlasDiagnosticView> diagnostics(AtlasPackage atlasPackage) {
        return atlasPackage.diagnostics().stream().map(diagnostic -> new AtlasDiagnosticView(
                diagnostic.severity().name(), diagnostic.code(), diagnostic.message(),
                diagnostic.locator().map(DefaultAtlasRoleViewQuery::location).orElseGet(DefaultAtlasRoleViewQuery::emptyLocation))).toList();
    }

    private enum Role { BUSINESS, OPS, EXPERT }

    private static final class Index {

        private final Map<AtlasId, ArchitectureAsset> assets;
        private final List<AtlasRelation> relations;
        private final Map<AtlasId, List<ContentEntry>> contentByAsset;

        private Index(AtlasPackage atlasPackage) {
            assets = atlasPackage.designAtlas().nodes().stream().collect(Collectors.toMap(
                    ArchitectureAsset::id, asset -> asset, (first, ignored) -> first, LinkedHashMap::new));
            relations = atlasPackage.designAtlas().relations();
            contentByAsset = atlasPackage.content().entries().stream().collect(Collectors.groupingBy(
                    ContentEntry::assetId, LinkedHashMap::new, Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                            .sorted(Comparator.comparing(ContentEntry::id)).toList())));
        }

        private Optional<ArchitectureAsset> asset(AtlasId id) {
            return Optional.ofNullable(assets.get(id));
        }

        private AtlasAssetReference reference(AtlasId id) {
            return asset(id).map(DefaultAtlasRoleViewQuery::reference)
                    .orElseGet(() -> new AtlasAssetReference(id.value(), id.value(), "UNRESOLVED", "当前 Package 缺少该关系目标"));
        }

        private <T extends ArchitectureAsset> List<T> assetsOf(Class<T> type) {
            return assets.values().stream().filter(type::isInstance).map(type::cast)
                    .sorted(Comparator.comparing(ArchitectureAsset::id)).toList();
        }

        private List<AtlasRelation> incoming(AtlasId id, RelationKind... kinds) {
            return matching(relation -> relation.targetId().equals(id), kinds);
        }

        private List<AtlasRelation> outgoing(AtlasId id, RelationKind... kinds) {
            return matching(relation -> relation.sourceId().equals(id), kinds);
        }

        private List<AtlasRelation> projectTargetRelations(AtlasId projectionId) {
            return outgoing(projectionId, RelationKind.PROJECTS_TO, RelationKind.WRITES_TO).stream()
                    .filter(relation -> asset(relation.targetId()).filter(DataTarget.class::isInstance).isPresent()).toList();
        }

        private List<CodeElement> representedCode(AtlasId semanticId) {
            return outgoing(semanticId, RelationKind.REPRESENTS).stream().map(AtlasRelation::targetId)
                    .map(this::asset).flatMap(Optional::stream).filter(CodeElement.class::isInstance).map(CodeElement.class::cast).toList();
        }

        private Optional<CodeElement> codeFor(AtlasId assetId) {
            Optional<CodeElement> direct = asset(assetId).filter(CodeElement.class::isInstance).map(CodeElement.class::cast);
            if (direct.isPresent()) {
                return direct;
            }
            List<CodeElement> represented = representedCode(assetId);
            return represented.size() == 1 ? Optional.of(represented.getFirst()) : Optional.empty();
        }

        private List<ContentEntry> contentFor(AtlasId assetId) {
            return contentByAsset.getOrDefault(assetId, List.of());
        }

        private List<AtlasRelation> matching(Predicate<AtlasRelation> endpoint, RelationKind... kinds) {
            Set<RelationKind> expected = kinds.length == 0 ? EnumSet.allOf(RelationKind.class) : EnumSet.copyOf(List.of(kinds));
            return relations.stream().filter(endpoint).filter(relation -> expected.contains(relation.kind()))
                    .sorted(Comparator.comparing(AtlasRelation::id)).toList();
        }
    }
}
