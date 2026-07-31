package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileResult;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHandlingStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHealthStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeObservation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeOverlay;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignal;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 将脱敏的运行态信号确定性关联到静态 Atlas 图。
 *
 * <p>该类不扫描源码、不读取数据库，也不把运行状态回写为设计事实。只有信号中的事件类型、处理器类型和静态
 * {@link RelationKind#CONSUMES} 关系同时唯一匹配，才会产生目标为 Projection 或 Process Manager 的运行态观测；
 * 其余情况成为诊断。每一个未获得可关联信号的可观测资产都会明确显示为 {@link RuntimeHealthStatus#UNKNOWN}，
 * 而不是错误地展示成成功或健康。</p>
 */
public final class DefaultRuntimeOverlayCompiler implements RuntimeOverlayCompileUseCase {

    private static final String ANALYZER_ID = "atlas-runtime-overlay-compiler";

    @Override
    public RuntimeOverlayCompileResult compile(RuntimeOverlayCompileRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        AtlasPackage atlasPackage = request.atlasPackage();
        Instant capturedAt = request.signalBatch().capturedAt();
        GraphIndex index = new GraphIndex(atlasPackage);
        List<RuntimeObservation> observations = new ArrayList<>();
        List<AtlasDiagnostic> diagnostics = new ArrayList<>();

        for (RuntimeSignal signal : request.signalBatch().signals()) {
            index.resolve(signal, diagnostics).ifPresent(target -> observations.add(observation(signal, target, index)));
        }

        Map<AtlasId, List<RuntimeObservation>> observationsBySubject = observations.stream()
                .collect(Collectors.groupingBy(RuntimeObservation::subjectId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        for (ArchitectureAsset asset : index.observableAssets()) {
            if (!observationsBySubject.containsKey(asset.id())) {
                RuntimeObservation unknown = unknownObservation(asset, capturedAt);
                observations.add(unknown);
                observationsBySubject.put(asset.id(), new ArrayList<>(List.of(unknown)));
            }
        }

        RuntimeOverlay overlay = new RuntimeOverlay(capturedAt, observations);
        var health = new RuntimeOverlayHealthResolver().assess(atlasPackage.designAtlas(), overlay);
        return new RuntimeOverlayCompileResult(
                overlay,
                health,
                diagnostics.stream().sorted(Comparator.comparing(AtlasDiagnostic::id)).toList());
    }

    private static RuntimeObservation observation(RuntimeSignal signal, Target target, GraphIndex index) {
        List<AtlasId> impactedTargets = index.impactedDataTargets(target.asset().id());
        Map<String, AtlasValue> attributes = new LinkedHashMap<>(signal.attributes());
        attributes.put("eventType", AtlasValue.text(signal.eventType()));
        attributes.put("handlerType", AtlasValue.text(signal.handlerType()));
        attributes.put("handlingStatus", AtlasValue.text(signal.status().name()));
        if (!impactedTargets.isEmpty()) {
            attributes.put("impactedDataTargetIds", AtlasValue.values(impactedTargets.stream()
                    .map(AtlasId::value)
                    .map(AtlasValue::text)
                    .map(AtlasValue.class::cast)
                    .toList()));
        }
        return new RuntimeObservation(
                AtlasId.of("runtime-observation:" + encode(signal.signalId() + ":" + target.asset().id().value())),
                target.asset().id(),
                signal.observedAt(),
                observationType(target.asset(), signal.status()),
                attributes,
                signal.evidence());
    }

    private static RuntimeObservation unknownObservation(ArchitectureAsset asset, Instant capturedAt) {
        return new RuntimeObservation(
                AtlasId.of("runtime-observation:" + encode("unknown:" + asset.id().value() + ":" + capturedAt)),
                asset.id(),
                capturedAt,
                observationType(asset, RuntimeHealthStatus.UNKNOWN),
                Map.of("handlingStatus", AtlasValue.text(RuntimeHealthStatus.UNKNOWN.name())),
                new Evidence(
                        AtlasId.of("evidence:" + ANALYZER_ID + ":" + encode(asset.id().value() + ":" + capturedAt)),
                        EvidenceOrigin.RUNTIME_SYSTEM,
                        EvidenceClaim.OBSERVED,
                        EvidenceStatus.VERIFIED,
                        new SourceLocator("runtime://atlas/overlay", asset.id().value(), SourceRange.unknown()),
                        capturedAt.toString(),
                        EvidenceVisibility.OPS_SAFE,
                        ANALYZER_ID,
                        "本次受控运行态采集未提供可唯一关联的处理记录；状态未知，不表示处理成功。"));
    }

    private static String observationType(ArchitectureAsset asset, RuntimeHandlingStatus status) {
        return observationPrefix(asset) + status.name();
    }

    private static String observationType(ArchitectureAsset asset, RuntimeHealthStatus status) {
        return observationPrefix(asset) + status.name();
    }

    private static String observationPrefix(ArchitectureAsset asset) {
        if (asset instanceof Projection) {
            return "PROJECTION_";
        }
        if (asset instanceof ProcessManager) {
            return "PROCESS_MANAGER_";
        }
        throw new IllegalArgumentException("运行态只支持 Projection 或 Process Manager: " + asset.id());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Target(DomainEvent event, ArchitectureAsset asset) {
        private Target {
            Objects.requireNonNull(event, "event 不能为空");
            Objects.requireNonNull(asset, "asset 不能为空");
        }
    }

    private static final class GraphIndex {

        private final Map<AtlasId, ArchitectureAsset> nodes;
        private final List<AtlasRelation> relations;
        private final Map<String, List<DomainEvent>> eventsByType;
        private final Map<String, List<ArchitectureAsset>> handlersByType;

        private GraphIndex(AtlasPackage atlasPackage) {
            nodes = atlasPackage.designAtlas().nodes().stream()
                    .collect(Collectors.toMap(ArchitectureAsset::id, asset -> asset, (first, ignored) -> first, LinkedHashMap::new));
            relations = atlasPackage.designAtlas().relations();
            Map<AtlasId, CodeElement> codeById = nodes.values().stream()
                    .filter(CodeElement.class::isInstance)
                    .map(CodeElement.class::cast)
                    .collect(Collectors.toMap(CodeElement::id, code -> code, (first, ignored) -> first, LinkedHashMap::new));
            eventsByType = representedAssets(DomainEvent.class, codeById);
            handlersByType = representedHandlers(codeById);
        }

        private Optional<Target> resolve(RuntimeSignal signal, List<AtlasDiagnostic> diagnostics) {
            List<DomainEvent> events = eventsByType.getOrDefault(signal.eventType(), List.of());
            if (events.size() != 1) {
                diagnostics.add(diagnostic(
                        events.isEmpty() ? "RUNTIME_EVENT_NOT_IN_STATIC_ATLAS" : "AMBIGUOUS_RUNTIME_EVENT",
                        "运行态事件类型无法唯一关联静态 Atlas 事件: " + signal.eventType(),
                        signal));
                return Optional.empty();
            }
            List<ArchitectureAsset> handlers = handlersByType.getOrDefault(signal.handlerType(), List.of());
            if (handlers.size() != 1) {
                diagnostics.add(diagnostic(
                        handlers.isEmpty() ? "RUNTIME_HANDLER_NOT_IN_STATIC_ATLAS" : "AMBIGUOUS_RUNTIME_HANDLER",
                        "运行态处理器类型无法唯一关联静态 Projection 或 Process Manager: " + signal.handlerType(),
                        signal));
                return Optional.empty();
            }
            DomainEvent event = events.getFirst();
            ArchitectureAsset handler = handlers.getFirst();
            boolean consumes = relations.stream().anyMatch(relation -> relation.kind() == RelationKind.CONSUMES
                    && relation.sourceId().equals(event.id()) && relation.targetId().equals(handler.id()));
            if (!consumes) {
                diagnostics.add(diagnostic(
                        "RUNTIME_HANDLER_EVENT_PAIR_NOT_STATIC",
                        "运行态事件与处理器在静态 Atlas 中不存在可证明的消费关系: "
                                + signal.eventType() + " -> " + signal.handlerType(),
                        signal));
                return Optional.empty();
            }
            return Optional.of(new Target(event, handler));
        }

        private List<ArchitectureAsset> observableAssets() {
            return nodes.values().stream()
                    .filter(asset -> asset instanceof Projection || asset instanceof ProcessManager)
                    .sorted(Comparator.comparing(ArchitectureAsset::id))
                    .toList();
        }

        private List<AtlasId> impactedDataTargets(AtlasId assetId) {
            return relations.stream()
                    .filter(relation -> relation.sourceId().equals(assetId))
                    .filter(relation -> relation.kind() == RelationKind.PROJECTS_TO || relation.kind() == RelationKind.WRITES_TO)
                    .map(AtlasRelation::targetId)
                    .filter(nodes::containsKey)
                    .filter(target -> nodes.get(target) instanceof DataTarget)
                    .distinct()
                    .sorted()
                    .toList();
        }

        private <T extends ArchitectureAsset> Map<String, List<T>> representedAssets(
                Class<T> assetType,
                Map<AtlasId, CodeElement> codeById) {
            Map<String, List<T>> result = new LinkedHashMap<>();
            for (AtlasRelation relation : relations) {
                if (relation.kind() != RelationKind.REPRESENTS) {
                    continue;
                }
                ArchitectureAsset source = nodes.get(relation.sourceId());
                CodeElement target = codeById.get(relation.targetId());
                if (assetType.isInstance(source) && target != null) {
                    result.computeIfAbsent(target.qualifiedName(), ignored -> new ArrayList<>()).add(assetType.cast(source));
                }
            }
            result.replaceAll((ignored, assets) -> assets.stream().distinct()
                    .sorted(Comparator.comparing(ArchitectureAsset::id)).toList());
            return result;
        }

        private Map<String, List<ArchitectureAsset>> representedHandlers(Map<AtlasId, CodeElement> codeById) {
            Map<String, List<ArchitectureAsset>> result = new LinkedHashMap<>();
            for (AtlasRelation relation : relations) {
                if (relation.kind() != RelationKind.REPRESENTS) {
                    continue;
                }
                ArchitectureAsset source = nodes.get(relation.sourceId());
                CodeElement target = codeById.get(relation.targetId());
                if ((source instanceof Projection || source instanceof ProcessManager) && target != null) {
                    result.computeIfAbsent(target.qualifiedName(), ignored -> new ArrayList<>()).add(source);
                }
            }
            result.replaceAll((ignored, assets) -> assets.stream().distinct()
                    .sorted(Comparator.comparing(ArchitectureAsset::id)).toList());
            return Map.copyOf(result);
        }

        private static AtlasDiagnostic diagnostic(String code, String message, RuntimeSignal signal) {
            return new AtlasDiagnostic(
                    AtlasId.of("diagnostic:" + ANALYZER_ID + ":" + encode(code + ":" + signal.signalId())),
                    DiagnosticSeverity.WARNING,
                    code,
                    message,
                    Optional.of(signal.evidence().locator()));
        }
    }
}
