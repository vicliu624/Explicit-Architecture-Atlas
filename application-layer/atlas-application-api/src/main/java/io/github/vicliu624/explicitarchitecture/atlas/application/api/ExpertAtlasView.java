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
            ExecutionGraph executionGraph,
            List<AtlasRelationView> directEffects) {
        public EndpointTrace {
            Objects.requireNonNull(endpoint, "endpoint 不能为空");
            httpMethods = immutable(httpMethods, "httpMethods");
            paths = immutable(paths, "paths");
            callPaths = immutable(callPaths, "callPaths");
            Objects.requireNonNull(executionGraph, "executionGraph 不能为空");
            directEffects = immutable(directEffects, "directEffects");
        }
    }

    /**
     * 一个入口可达的执行关系图，不把分支和循环强行压成有限数量的简单路径。
     *
     * <p>{@link CallPath} 只是界面的快速预览，可能因可读性而截断；该图则包含从入口出发经同步调用、命令派发、
     * Handler、事件消费、流程协调、投影和数据目标等所有已证明的可达节点和边。{@code traversalDirection} 说明
     * 为了表达“命令 → Handler”而反向使用了一条语义上仍为 {@code HANDLES} 的原始关系。</p>
     */
    public record ExecutionGraph(List<TraceNode> nodes, List<TraceRelation> relations) {
        public ExecutionGraph {
            nodes = immutable(nodes, "nodes");
            relations = immutable(relations, "relations");
        }
    }

    /** 执行图节点；代码节点携带完整限定符和受控项目源码定位。 */
    public record TraceNode(AtlasAssetReference asset, String qualifiedName, AtlasLocationView location) {
        public TraceNode {
            Objects.requireNonNull(asset, "asset 不能为空");
            qualifiedName = qualifiedName == null ? "" : qualifiedName.trim();
            Objects.requireNonNull(location, "location 不能为空");
        }
    }

    /** 执行图边及其完整证据；方向只影响遍历/渲染，不修改底层事实关系。 */
    public record TraceRelation(AtlasRelationView relation, String traversalDirection) {
        public TraceRelation {
            Objects.requireNonNull(relation, "relation 不能为空");
            traversalDirection = required(traversalDirection, "traversalDirection");
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
            List<FunctionContract> functionContracts,
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
            functionContracts = immutable(functionContracts, "functionContracts");
            runtimeObservations = immutable(runtimeObservations, "runtimeObservations");
            Objects.requireNonNull(source, "source 不能为空");
        }
    }

    /**
     * 某个源码方法在 Atlas 图中的可执行契约。
     *
     * <p>这不是把 Java 签名复制成一段字符串：每个输入、输出均指向独立的语义值资产，关联边说明调用方的哪一个
     * 实参已经被证明绑定到该参数，以及该方法中哪一个输入直接流向返回值。无法静态唯一解析的流不会伪造到此视图。
     * 一个代码元素理论上可被多个契约表示，因此使用列表以保留这种显式多义性。</p>
     */
    public record FunctionContract(
            AtlasAssetReference operation,
            String javaSignature,
            List<FunctionValue> inputs,
            List<FunctionValue> outputs) {
        public FunctionContract {
            Objects.requireNonNull(operation, "operation 不能为空");
            javaSignature = required(javaSignature, "javaSignature");
            inputs = immutable(inputs, "inputs");
            outputs = immutable(outputs, "outputs");
        }
    }

    /** 函数输入或输出值，以及与其他函数参数/返回值的已证明关联。 */
    public record FunctionValue(
            String role,
            AtlasAssetReference value,
            List<AtlasProperty> properties,
            List<AtlasRelationView> associations) {
        public FunctionValue {
            role = required(role, "role");
            Objects.requireNonNull(value, "value 不能为空");
            properties = immutable(properties, "properties");
            associations = immutable(associations, "associations");
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

    /** 资产是否可以从 Workspace 声明的项目源根或 source JAR 安全解析。 */
    public record SourceReference(boolean available, String qualifiedName, AtlasLocationView location) {
        public SourceReference {
            qualifiedName = qualifiedName == null ? "" : qualifiedName.trim();
            Objects.requireNonNull(location, "location 不能为空");
        }
    }

    /** 项目源根或 source JAR 命中的源码文本；不会返回本机绝对路径。 */
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
