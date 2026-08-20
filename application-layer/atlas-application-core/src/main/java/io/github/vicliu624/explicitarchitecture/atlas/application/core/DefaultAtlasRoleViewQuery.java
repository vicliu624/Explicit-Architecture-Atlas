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
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentDefinition;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentEntry;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ExternalBoundary;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionPoint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationContract;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationFailure;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInput;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcome;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeObservation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValue;
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
    public Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTrace(AtlasId packageId, AtlasId endpointId) {
        Objects.requireNonNull(endpointId, "endpointId 不能为空");
        return atlasPackage(packageId).flatMap(atlasPackage -> {
            Index index = new Index(atlasPackage);
            return index.asset(endpointId).filter(EndpointOperation.class::isInstance).map(EndpointOperation.class::cast)
                    .map(endpoint -> endpointTrace(index, endpoint, true));
        });
    }

    @Override
    public Optional<ExpertAtlasView.EndpointTrace> findExpertEndpointTraceByHandler(AtlasId packageId, String handlerQualifiedName) {
        String handler = Objects.requireNonNull(handlerQualifiedName, "handlerQualifiedName 不能为空").trim();
        if (handler.isEmpty()) {
            throw new IllegalArgumentException("handlerQualifiedName 不能为空白");
        }
        return atlasPackage(packageId).flatMap(atlasPackage -> {
            Index index = new Index(atlasPackage);
            List<EndpointOperation> matches = index.assetsOf(EndpointOperation.class).stream()
                    .filter(endpoint -> index.representedCode(endpoint.id()).stream()
                            .anyMatch(code -> code.qualifiedName().equals(handler)
                                    || (!handler.contains("(") && code.qualifiedName().startsWith(handler + "("))))
                    .toList();
            return matches.size() == 1 ? Optional.of(endpointTrace(index, matches.getFirst(), true)) : Optional.empty();
        });
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
                index.assetsOf(EndpointOperation.class).stream().map(endpoint -> endpointTrace(index, endpoint, false)).toList(),
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

    private ExpertAtlasView.EndpointTrace endpointTrace(Index index, EndpointOperation endpoint, boolean includeExecutionGraph) {
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
                includeExecutionGraph ? executionGraph(index, endpoint) : new ExpertAtlasView.ExecutionGraph(List.of(), List.of()),
                relationViews(effects, index, Role.EXPERT));
    }

    /**
     * 以图而非“若干条简单路径”表达入口完整可达关系。
     *
     * <p>一条路径预览必须限制深度和数量，否则分支/循环会使 UI 无法使用；但这不应让 Package 丢失可达的 Handler、
     * 事件消费者或投影。该图以 visited 节点保证有限，并保留每一条原始关系的证据和遍历方向。调用方可以用它绘制
     * 全图、分段展开，或自行选择一条路径，而不会把预览上限误解为架构边界。</p>
     */
    private ExpertAtlasView.ExecutionGraph executionGraph(Index index, EndpointOperation endpoint) {
        Map<AtlasId, ArchitectureAsset> reachable = new LinkedHashMap<>();
        Map<String, ExpertAtlasView.TraceRelation> transitions = new LinkedHashMap<>();
        List<AtlasId> pending = new ArrayList<>(List.of(endpoint.id()));
        Set<AtlasId> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            AtlasId current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            ArchitectureAsset currentAsset = index.asset(current).orElse(null);
            if (currentAsset == null) {
                continue;
            }
            reachable.put(current, currentAsset);
            for (ExecutionTransition transition : executionTransitions(index, current)) {
                ArchitectureAsset target = index.asset(transition.targetId()).orElse(null);
                if (target == null) {
                    continue;
                }
                reachable.put(target.id(), target);
                AtlasRelationView relation = relationViews(List.of(transition.relation()), index, Role.EXPERT).getFirst();
                // 同一条原始关系可能因 REPRESENTS 的正反遍历再次被看见。图的边以事实 relation id 去重；首次
                // 到达方向保留为此次执行链的导航方向，防止 UI 把一个事实渲染成两条重复连线。
                transitions.putIfAbsent(relation.id(), new ExpertAtlasView.TraceRelation(relation, transition.direction()));
                if (!visited.contains(target.id())) {
                    pending.add(target.id());
                }
            }
        }
        return new ExpertAtlasView.ExecutionGraph(
                reachable.values().stream().sorted(Comparator.comparing(ArchitectureAsset::id))
                        .map(DefaultAtlasRoleViewQuery::traceNode).toList(),
                transitions.values().stream().sorted(Comparator.comparing((ExpertAtlasView.TraceRelation item) -> item.relation().id())
                        .thenComparing(ExpertAtlasView.TraceRelation::traversalDirection)).toList());
    }

    private List<ExecutionTransition> executionTransitions(Index index, AtlasId current) {
        List<ExecutionTransition> transitions = new ArrayList<>();
        index.outgoing(current,
                        RelationKind.REPRESENTS,
                        RelationKind.CALLS,
                        RelationKind.DISPATCHES,
                        RelationKind.PRODUCES,
                        RelationKind.CONSUMES,
                        RelationKind.PUBLISHES,
                        RelationKind.PROJECTS_TO,
                        RelationKind.WRITES_TO,
                        RelationKind.READS_FROM,
                        RelationKind.CALLS_EXTERNAL,
                        RelationKind.STARTS,
                        RelationKind.WAITS_FOR,
                        RelationKind.COMPLETES,
                        RelationKind.COMPENSATES,
                        RelationKind.CONSTRAINS,
                        RelationKind.VALIDATES,
                        RelationKind.MUTATES_STATE)
                .forEach(relation -> transitions.add(new ExecutionTransition(relation, relation.targetId(), "FORWARD")));

        // Command 的语义边方向是 Handler --HANDLES--> Command；从一次派发继续执行时需要反向抵达 Handler。
        index.incoming(current, RelationKind.HANDLES)
                .forEach(relation -> transitions.add(new ExecutionTransition(relation, relation.sourceId(), "REVERSE")));
        // DomainConstraint 的事实方向是 “约束 --CONSTRAINS--> 被约束代码”。沿入口执行到该代码后，必须反向
        // 抵达约束资产，才能让接口链路同时解释业务判定及其源码强制点。
        index.incoming(current, RelationKind.CONSTRAINS)
                .forEach(relation -> transitions.add(new ExecutionTransition(relation, relation.sourceId(), "REVERSE")));
        // 代码本身的副作用通常挂在 Operation/Endpoint 等语义资产上。反向回到这些 owner 后可继续前进到
        // DISPATCHES / PRODUCES；visited 集合会消除与正向 REPRESENTS 的往返循环。
        index.asset(current).filter(CodeElement.class::isInstance).ifPresent(ignored -> index.incoming(current, RelationKind.REPRESENTS)
                .forEach(relation -> transitions.add(new ExecutionTransition(relation, relation.sourceId(), "REVERSE"))));
        return transitions.stream().sorted(Comparator.comparing((ExecutionTransition item) -> item.relation().id())
                .thenComparing(ExecutionTransition::direction)).toList();
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
                properties(asset),
                allNarratives(index, asset.id()),
                asset.evidence().stream().filter(evidence -> visibleTo(evidence, Role.EXPERT)).map(DefaultAtlasRoleViewQuery::evidence).toList(),
                relationViews(index.incoming(asset.id()), index, Role.EXPERT),
                relationViews(index.outgoing(asset.id()), index, Role.EXPERT),
                functionContracts(index, asset),
                runtimeObservations(atlasPackage, asset.id()),
                source.map(code -> new ExpertAtlasView.SourceReference(
                                code.locator().uri().startsWith("project://"), code.qualifiedName(), location(code.locator())))
                        .orElseGet(() -> new ExpertAtlasView.SourceReference(false, "", emptyLocation())));
    }

    /**
     * 将源码元素背后的 OperationContract 展开到同一个专家详情中。
     *
     * <p>源码分析阶段已经保存 ACCEPTS / RETURNS / ARGUMENT_BINDS_TO / RETURN_FLOWS_TO；若只展示代码元素的
     * CALLS 边，使用者仍需自行跨越 REPRESENTS 边才能解释函数契约。这里仅投影已有事实，且关联边仍保留完整
     * evidence，不能唯一解析的实参绑定自然不会出现。</p>
     */
    private static List<ExpertAtlasView.FunctionContract> functionContracts(Index index, ArchitectureAsset asset) {
        List<OperationContract> operations = new ArrayList<>();
        if (asset instanceof OperationContract operation) {
            operations.add(operation);
        }
        index.incoming(asset.id(), RelationKind.REPRESENTS).stream()
                .map(AtlasRelation::sourceId).map(index::asset).flatMap(Optional::stream)
                .filter(OperationContract.class::isInstance).map(OperationContract.class::cast)
                .forEach(operations::add);
        return operations.stream().collect(Collectors.toMap(OperationContract::id, operation -> operation,
                        (first, ignored) -> first, LinkedHashMap::new)).values().stream()
                .sorted(Comparator.comparing(OperationContract::id))
                .map(operation -> functionContract(index, operation)).toList();
    }

    private static ExpertAtlasView.FunctionContract functionContract(Index index, OperationContract operation) {
        List<ExpertAtlasView.FunctionValue> inputs = index.outgoing(operation.id(), RelationKind.ACCEPTS).stream()
                .map(AtlasRelation::targetId).map(index::asset).flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(DefaultAtlasRoleViewQuery::parameterIndex)
                        .thenComparing(ArchitectureAsset::id))
                .map(value -> functionValue(index, value, "INPUT", RelationKind.ARGUMENT_BINDS_TO)).toList();
        List<ExpertAtlasView.FunctionValue> outputs = index.outgoing(operation.id(), RelationKind.RETURNS).stream()
                .map(AtlasRelation::targetId).map(index::asset).flatMap(Optional::stream)
                .sorted(Comparator.comparing(ArchitectureAsset::id))
                .map(value -> functionValue(index, value, "RETURN", RelationKind.RETURN_FLOWS_TO)).toList();
        return new ExpertAtlasView.FunctionContract(reference(operation), operation.javaSignature(), inputs, outputs);
    }

    private static ExpertAtlasView.FunctionValue functionValue(
            Index index,
            ArchitectureAsset value,
            String role,
            RelationKind associationKind) {
        return new ExpertAtlasView.FunctionValue(role, reference(value), properties(value),
                relationViews(index.incoming(value.id(), associationKind), index, Role.EXPERT));
    }

    private static int parameterIndex(ArchitectureAsset asset) {
        AtlasValue index = asset.attributes().get("parameterIndex");
        return index instanceof AtlasValue.Decimal decimal ? decimal.value().intValue() : Integer.MAX_VALUE;
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

    private static ExpertAtlasView.TraceNode traceNode(ArchitectureAsset asset) {
        if (asset instanceof CodeElement code) {
            return new ExpertAtlasView.TraceNode(reference(code), code.qualifiedName(), location(code.locator()));
        }
        return new ExpertAtlasView.TraceNode(reference(asset), "", emptyLocation());
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

    /**
     * 将类型化资产的字段和可扩展 attributes 一并投影到专家 API。关系仍单独返回，避免把一张图压扁成不可追溯的文本；
     * 这里的字段则让调用方无需理解 domain record 的 Java 序列化细节也能解释入参、返回、条件和注入选择。
     */
    private static List<AtlasProperty> properties(ArchitectureAsset asset) {
        Map<String, String> values = new LinkedHashMap<>();
        asset.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put("attribute." + entry.getKey(), render(entry.getValue())));
        switch (asset) {
            case SemanticValue value -> {
                values.put("semantic.category", value.category().name());
                values.put("semantic.javaRepresentation", value.javaRepresentation());
                values.put("semantic.cardinality", value.cardinality().name());
                values.put("semantic.nullability", value.nullability().name());
                values.put("semantic.trustLevel", value.trustLevel().name());
                values.put("semantic.sensitivity", value.sensitivity().name());
                values.put("semantic.lifecycleState", value.lifecycleState());
            }
            case OperationContract operation -> addOperationProperties(values, operation);
            case ComponentDefinition component -> {
                values.put("component.kind", component.componentKind().name());
                values.put("component.beanName", component.beanName());
                values.put("component.declaredType", component.declaredType());
                values.put("component.candidateType", component.candidateType());
                values.put("component.scope", component.scope().name());
                values.put("component.primary", Boolean.toString(component.primary()));
                addList(values, "component.qualifier", component.qualifiers());
                addConditionProperties(values, "component.activation", component.activationCondition());
            }
            case InjectionPoint injection -> {
                values.put("injection.ownerComponentId", injection.ownerComponentId().value());
                values.put("injection.requestedType", injection.requestedType());
                values.put("injection.kind", injection.injectionKind().name());
                values.put("injection.cardinality", injection.cardinality().name());
                values.put("injection.required", Boolean.toString(injection.required()));
                addList(values, "injection.qualifier", injection.qualifiers());
            }
            case ConfigurationProperty property -> {
                values.put("configuration.key", property.propertyKey());
                values.put("configuration.declaredDefault", property.declaredDefault());
                values.put("configuration.sensitivity", property.sensitivity().name());
            }
            case ConfigurationScenario scenario -> {
                addList(values, "scenario.activeProfile", scenario.activeProfiles());
                addList(values, "scenario.classpathCapability", scenario.classpathCapabilities());
                values.put("scenario.classpathCompleteness", scenario.classpathCapabilityCompleteness().name());
                values.put("scenario.applicationContext.id", scenario.applicationContext().contextId());
                addList(values, "scenario.applicationContext.entryPointType", scenario.applicationContext().entryPointTypes());
                addList(values, "scenario.applicationContext.rootModule", scenario.applicationContext().rootModuleCoordinates());
                addList(values, "scenario.applicationContext.sourcePathPrefix", scenario.applicationContext().sourcePathPrefixes());
                addList(values, "scenario.applicationContext.componentScanPackage", scenario.applicationContext().componentScanPackages());
                values.put("scenario.applicationContext.beanDefinitionOverridePolicy",
                        scenario.applicationContext().beanDefinitionOverridePolicy().name());
                scenario.applicationContext().explicitBeanOverrides().forEach((beanName, candidateType) ->
                        values.put("scenario.applicationContext.explicitBeanOverride[" + beanName + "]", candidateType));
                scenario.properties().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    String prefix = "scenario.property[" + entry.getKey() + "]";
                    values.put(prefix + ".provenance", entry.getValue().provenance());
                    values.put(prefix + ".redacted", Boolean.toString(entry.getValue().redacted()));
                    values.put(prefix + ".value", entry.getValue().redacted() ? "<redacted>" : entry.getValue().value());
                });
            }
            default -> {
                // 标准业务资产只使用显式 attributes；类型化扩展在上面的分支中展示。
            }
        }
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new AtlasProperty(entry.getKey(), entry.getValue())).toList();
    }

    private static void addOperationProperties(Map<String, String> values, OperationContract operation) {
        values.put("operation.kind", operation.operationKind().name());
        values.put("operation.javaSignature", operation.javaSignature());
        values.put("operation.inputCount", Integer.toString(operation.inputs().size()));
        for (int index = 0; index < operation.inputs().size(); index++) {
            OperationInput input = operation.inputs().get(index);
            String prefix = "operation.input[" + index + "]";
            values.put(prefix + ".name", input.name());
            values.put(prefix + ".javaType", input.javaType());
            values.put(prefix + ".role", input.role().name());
            values.put(prefix + ".implicit", Boolean.toString(input.implicit()));
            values.put(prefix + ".nullability", input.nullability().name());
            values.put(prefix + ".description", input.description());
        }
        values.put("operation.outcomeCount", Integer.toString(operation.outcomes().size()));
        for (int index = 0; index < operation.outcomes().size(); index++) {
            OperationOutcome outcome = operation.outcomes().get(index);
            String prefix = "operation.outcome[" + index + "]";
            values.put(prefix + ".kind", outcome.kind().name());
            values.put(prefix + ".name", outcome.name());
            values.put(prefix + ".javaType", outcome.javaType());
            values.put(prefix + ".condition", outcome.condition());
            values.put(prefix + ".description", outcome.description());
        }
        values.put("operation.failureCount", Integer.toString(operation.failures().size()));
        for (int index = 0; index < operation.failures().size(); index++) {
            OperationFailure failure = operation.failures().get(index);
            String prefix = "operation.failure[" + index + "]";
            values.put(prefix + ".exceptionType", failure.exceptionType());
            values.put(prefix + ".condition", failure.condition());
            values.put(prefix + ".description", failure.description());
        }
    }

    private static void addConditionProperties(
            Map<String, String> values,
            String prefix,
            io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionExpression condition) {
        values.put(prefix + ".canonicalForm", condition.canonicalForm());
        values.put(prefix + ".status", condition.status().name());
        for (int index = 0; index < condition.terms().size(); index++) {
            var term = condition.terms().get(index);
            String termPrefix = prefix + ".term[" + index + "]";
            values.put(termPrefix + ".kind", term.kind());
            values.put(termPrefix + ".subject", term.subject());
            values.put(termPrefix + ".operator", term.operator());
            values.put(termPrefix + ".expectedValue", term.expectedValue());
        }
    }

    private static void addList(Map<String, String> values, String prefix, List<String> entries) {
        values.put(prefix + "Count", Integer.toString(entries.size()));
        for (int index = 0; index < entries.size(); index++) {
            values.put(prefix + "[" + index + "]", entries.get(index));
        }
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
            case SemanticValue value -> value.category().name() + ": " + value.javaRepresentation();
            case OperationContract operation -> operation.javaSignature();
            case ComponentDefinition component -> component.beanName() + ": " + component.candidateType();
            case InjectionPoint injection -> injection.requestedType();
            case ConfigurationProperty property -> property.propertyKey();
            case ConfigurationScenario scenario -> "context=" + scenario.applicationContext().contextId()
                    + "; profiles=" + String.join(",", scenario.activeProfiles());
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

    private record ExecutionTransition(AtlasRelation relation, AtlasId targetId, String direction) {
        private ExecutionTransition {
            Objects.requireNonNull(relation, "relation 不能为空");
            Objects.requireNonNull(targetId, "targetId 不能为空");
            direction = Objects.requireNonNull(direction, "direction 不能为空");
        }
    }

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
