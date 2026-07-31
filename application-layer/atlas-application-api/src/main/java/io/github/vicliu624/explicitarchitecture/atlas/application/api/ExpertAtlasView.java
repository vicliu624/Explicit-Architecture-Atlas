package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.List;
import java.util.Objects;

/** 面向开发与架构专家的关系、调用链和受控源码定位视图。 */
public record ExpertAtlasView(
        AtlasViewSummary summary,
        List<EventTrace> eventTraces,
        List<EndpointTrace> endpointTraces,
        List<SourceBackedAsset> sourceBackedAssets,
        List<AtlasDiagnosticView> diagnostics) {

    public ExpertAtlasView {
        Objects.requireNonNull(summary, "summary 不能为空");
        eventTraces = immutable(eventTraces, "eventTraces");
        endpointTraces = immutable(endpointTraces, "endpointTraces");
        sourceBackedAssets = immutable(sourceBackedAssets, "sourceBackedAssets");
        diagnostics = immutable(diagnostics, "diagnostics");
    }

    /** 事件的生产、消费、投影落点和每条关系的证明。 */
    public record EventTrace(
            AtlasAssetReference event,
            String businessFact,
            List<AtlasRelationView> productionRelations,
            List<AtlasRelationView> consumptionRelations,
            List<ProjectionDestination> projectionDestinations) {
        public EventTrace {
            Objects.requireNonNull(event, "event 不能为空");
            businessFact = required(businessFact, "businessFact");
            productionRelations = immutable(productionRelations, "productionRelations");
            consumptionRelations = immutable(consumptionRelations, "consumptionRelations");
            projectionDestinations = immutable(projectionDestinations, "projectionDestinations");
        }
    }

    /** Projection 与其静态落点之间的可追溯关系。 */
    public record ProjectionDestination(
            AtlasAssetReference projection,
            List<AtlasRelationView> targetRelations,
            List<AtlasAssetReference> dataTargets) {
        public ProjectionDestination {
            Objects.requireNonNull(projection, "projection 不能为空");
            targetRelations = immutable(targetRelations, "targetRelations");
            dataTargets = immutable(dataTargets, "dataTargets");
        }
    }

    /** HTTP 操作到已证明的 Java 调用树及其直接业务效果。 */
    public record EndpointTrace(
            AtlasAssetReference endpoint,
            List<String> httpMethods,
            List<String> paths,
            List<CallPath> callPaths,
            List<AtlasRelationView> directEffects) {
        public EndpointTrace {
            Objects.requireNonNull(endpoint, "endpoint 不能为空");
            httpMethods = immutable(httpMethods, "httpMethods");
            paths = immutable(paths, "paths");
            callPaths = immutable(callPaths, "callPaths");
            directEffects = immutable(directEffects, "directEffects");
        }
    }

    /** 一条有限深度、无循环的静态调用路径。 */
    public record CallPath(List<TraceStep> steps) {
        public CallPath {
            steps = immutable(steps, "steps");
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("调用路径至少需要一个步骤");
            }
        }
    }

    /** 路径中的一个资产；relationFromPrevious 为空表示路径起点。 */
    public record TraceStep(
            AtlasAssetReference asset,
            String relationFromPrevious,
            String qualifiedName,
            AtlasLocationView location) {
        public TraceStep {
            Objects.requireNonNull(asset, "asset 不能为空");
            relationFromPrevious = relationFromPrevious == null ? "" : relationFromPrevious.trim();
            qualifiedName = qualifiedName == null ? "" : qualifiedName.trim();
            Objects.requireNonNull(location, "location 不能为空");
        }
    }

    /** 可由本地 source JAR 根据受控定位符进一步打开的源码资产。 */
    public record SourceBackedAsset(AtlasAssetReference asset, String qualifiedName, AtlasLocationView location) {
        public SourceBackedAsset {
            Objects.requireNonNull(asset, "asset 不能为空");
            qualifiedName = required(qualifiedName, "qualifiedName");
            Objects.requireNonNull(location, "location 不能为空");
        }
    }

    /** 一个专家模式资产详情；源码正文必须经独立、受控的 source 查询获取。 */
    public record AssetDetail(
            AtlasViewSummary summary,
            AtlasAssetReference asset,
            List<AtlasProperty> properties,
            List<AtlasNarrative> narratives,
            List<AtlasEvidenceView> evidence,
            List<AtlasRelationView> incomingRelations,
            List<AtlasRelationView> outgoingRelations,
            List<RuntimeObservation> runtimeObservations,
            SourceReference source) {
        public AssetDetail {
            Objects.requireNonNull(summary, "summary 不能为空");
            Objects.requireNonNull(asset, "asset 不能为空");
            properties = immutable(properties, "properties");
            narratives = immutable(narratives, "narratives");
            evidence = immutable(evidence, "evidence");
            incomingRelations = immutable(incomingRelations, "incomingRelations");
            outgoingRelations = immutable(outgoingRelations, "outgoingRelations");
            runtimeObservations = immutable(runtimeObservations, "runtimeObservations");
            Objects.requireNonNull(source, "source 不能为空");
        }
    }

    /** 已脱敏的 Runtime Overlay 记录，供专家关联静态链路和运行事实。 */
    public record RuntimeObservation(
            String id,
            String observedAt,
            String observationType,
            List<AtlasProperty> safeAttributes,
            List<AtlasEvidenceView> evidence) {
        public RuntimeObservation {
            id = required(id, "id");
            observedAt = required(observedAt, "observedAt");
            observationType = required(observationType, "observationType");
            safeAttributes = immutable(safeAttributes, "safeAttributes");
            evidence = immutable(evidence, "evidence");
        }
    }

    /** 资产是否可以从 Workspace 声明的 source JAR 安全解析。 */
    public record SourceReference(boolean available, String qualifiedName, AtlasLocationView location) {
        public SourceReference {
            qualifiedName = qualifiedName == null ? "" : qualifiedName.trim();
            Objects.requireNonNull(location, "location 不能为空");
        }
    }

    /** source JAR 命中的源码文本；不会返回归档的本机绝对路径。 */
    public record SourceFile(
            String assetId,
            String qualifiedName,
            AtlasLocationView location,
            String archiveEntry,
            String content) {
        public SourceFile {
            assetId = required(assetId, "assetId");
            qualifiedName = required(qualifiedName, "qualifiedName");
            Objects.requireNonNull(location, "location 不能为空");
            archiveEntry = required(archiveEntry, "archiveEntry");
            Objects.requireNonNull(content, "content 不能为空");
        }
    }

    private static <T> List<T> immutable(List<T> values, String label) {
        return List.copyOf(Objects.requireNonNull(values, label + " 不能为空"));
    }

    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " 不能为空白");
        }
        return value;
    }
}
