package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import java.util.List;
import java.util.Objects;

/**
 * 面向业务人员的 Atlas 解释视图。
 *
 * <p>该视图只使用领域术语、流程目的和数据影响，不要求读者理解 Java、Axon 或 JPA。源码证据与调用树属于
 * {@link ExpertAtlasView}，运行处置属于 {@link OperationsAtlasView}。</p>
 */
public record BusinessAtlasView(
        AtlasViewSummary summary,
        List<Concept> concepts,
        List<Rule> rules,
        List<Event> events,
        List<Projection> projections,
        List<Process> processes,
        List<Endpoint> endpoints) {

    public BusinessAtlasView {
        Objects.requireNonNull(summary, "summary 不能为空");
        concepts = immutable(concepts, "concepts");
        rules = immutable(rules, "rules");
        events = immutable(events, "events");
        projections = immutable(projections, "projections");
        processes = immutable(processes, "processes");
        endpoints = immutable(endpoints, "endpoints");
    }

    private static <T> List<T> immutable(List<T> values, String label) {
        return List.copyOf(Objects.requireNonNull(values, label + " 不能为空"));
    }

    /** 业务概念及其职责。 */
    public record Concept(AtlasAssetReference asset, String responsibility, List<AtlasNarrative> narratives) {
        public Concept {
            Objects.requireNonNull(asset, "asset 不能为空");
            responsibility = required(responsibility, "responsibility");
            narratives = immutable(narratives, "narratives");
        }
    }

    /** 可读的领域规则；例如身份格式、跨字段约束或状态限制。 */
    public record Rule(
            AtlasAssetReference asset,
            AtlasAssetReference subject,
            String constraintKind,
            String enforcementLevel,
            String statement,
            String expression,
            List<AtlasNarrative> narratives) {
        public Rule {
            Objects.requireNonNull(asset, "asset 不能为空");
            Objects.requireNonNull(subject, "subject 不能为空");
            constraintKind = required(constraintKind, "constraintKind");
            enforcementLevel = required(enforcementLevel, "enforcementLevel");
            statement = required(statement, "statement");
            expression = expression == null ? "" : expression.trim();
            narratives = immutable(narratives, "narratives");
        }
    }

    /** 业务事件、携带字段、生产方、消费者及最终可见的数据影响。 */
    public record Event(
            AtlasAssetReference asset,
            String businessFact,
            List<EventField> payload,
            List<AtlasAssetReference> producers,
            List<Consumer> consumers,
            List<ProjectionOutput> projectionOutputs,
            List<AtlasNarrative> narratives) {
        public Event {
            Objects.requireNonNull(asset, "asset 不能为空");
            businessFact = required(businessFact, "businessFact");
            payload = immutable(payload, "payload");
            producers = immutable(producers, "producers");
            consumers = immutable(consumers, "consumers");
            projectionOutputs = immutable(projectionOutputs, "projectionOutputs");
            narratives = immutable(narratives, "narratives");
        }
    }

    /** 事件字段的角色和业务含义。 */
    public record EventField(String name, String role, String businessMeaning) {
        public EventField {
            name = required(name, "name");
            role = required(role, "role");
            businessMeaning = businessMeaning == null ? "" : businessMeaning.trim();
        }
    }

    /** 某个事件消费者及其业务角色（投影、自动流程等）。 */
    public record Consumer(AtlasAssetReference asset, String role) {
        public Consumer {
            Objects.requireNonNull(asset, "asset 不能为空");
            role = required(role, "role");
        }
    }

    /** 事件经过某个 Projection 后会更新的可见数据目标。 */
    public record ProjectionOutput(AtlasAssetReference projection, String purpose, List<AtlasAssetReference> dataTargets) {
        public ProjectionOutput {
            Objects.requireNonNull(projection, "projection 不能为空");
            purpose = required(purpose, "purpose");
            dataTargets = immutable(dataTargets, "dataTargets");
        }
    }

    /** 一个读模型的用途、输入事件和写入目标。 */
    public record Projection(
            AtlasAssetReference asset,
            String purpose,
            List<AtlasAssetReference> consumedEvents,
            List<AtlasAssetReference> dataTargets,
            List<AtlasNarrative> narratives) {
        public Projection {
            Objects.requireNonNull(asset, "asset 不能为空");
            purpose = required(purpose, "purpose");
            consumedEvents = immutable(consumedEvents, "consumedEvents");
            dataTargets = immutable(dataTargets, "dataTargets");
            narratives = immutable(narratives, "narratives");
        }
    }

    /** Saga / Process Manager 的启动、等待、完成条件及后续业务动作。 */
    public record Process(
            AtlasAssetReference asset,
            String responsibility,
            String completionCondition,
            List<AtlasAssetReference> startEvents,
            List<AtlasAssetReference> waitingEvents,
            List<AtlasAssetReference> completionEvents,
            List<AtlasAssetReference> dispatchedCommands,
            List<AtlasNarrative> narratives) {
        public Process {
            Objects.requireNonNull(asset, "asset 不能为空");
            responsibility = required(responsibility, "responsibility");
            completionCondition = completionCondition == null ? "" : completionCondition.trim();
            startEvents = immutable(startEvents, "startEvents");
            waitingEvents = immutable(waitingEvents, "waitingEvents");
            completionEvents = immutable(completionEvents, "completionEvents");
            dispatchedCommands = immutable(dispatchedCommands, "dispatchedCommands");
            narratives = immutable(narratives, "narratives");
        }
    }

    /** 面向外部调用者的业务接口，不暴露其内部 Java 调用细节。 */
    public record Endpoint(
            AtlasAssetReference asset,
            String purpose,
            List<String> httpMethods,
            List<String> paths,
            List<AtlasAssetReference> dispatchedCommands,
            List<AtlasNarrative> narratives) {
        public Endpoint {
            Objects.requireNonNull(asset, "asset 不能为空");
            purpose = required(purpose, "purpose");
            httpMethods = immutable(httpMethods, "httpMethods");
            paths = immutable(paths, "paths");
            dispatchedCommands = immutable(dispatchedCommands, "dispatchedCommands");
            narratives = immutable(narratives, "narratives");
        }
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
