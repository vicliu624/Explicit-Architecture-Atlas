package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Audience;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BoundaryKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BeanDefinitionOverridePolicy;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ClasspathCapabilityCompleteness;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentDefinition;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionExpression;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionTerm;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentEntry;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTargetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataSensitivity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraintKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EnforcementLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventField;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventFieldRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ExternalBoundary;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentDeclaration;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionPoint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationContract;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationFailure;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInput;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInputRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcome;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcomeKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeObservation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeOverlay;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValueCategory;
import io.github.vicliu624.explicitarchitecture.atlas.domain.TrustLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueNullability;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Atlas Package 1.0 的显式 JSON 编解码器；领域模型保持零 JSON 框架依赖。 */
final class AtlasPackageJsonCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    void write(Path file, AtlasPackage atlasPackage) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), packageNode(atlasPackage));
    }

    AtlasPackage read(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Atlas Package 必须是 JSON 对象: " + file);
        }
        return readPackage(root);
    }

    private ObjectNode packageNode(AtlasPackage value) {
        ObjectNode node = mapper.createObjectNode();
        node.set("manifest", manifestNode(value.manifest()));
        ObjectNode graph = node.putObject("designAtlas");
        graph.set("nodes", array(value.designAtlas().nodes(), this::node));
        graph.set("relations", array(value.designAtlas().relations(), this::relationNode));
        node.set("facts", factsNode(value.facts()));
        node.set("intents", intentsNode(value.intents()));
        node.set("content", contentNode(value.content()));
        value.runtimeOverlay().ifPresent(runtime -> node.set("runtimeOverlay", runtimeNode(runtime)));
        node.set("diagnostics", array(value.diagnostics(), this::diagnosticNode));
        return node;
    }

    private ObjectNode manifestNode(AtlasPackageManifest manifest) {
        ObjectNode node = mapper.createObjectNode();
        node.put("schemaVersion", manifest.schemaVersion());
        node.put("packageId", manifest.packageId().value());
        node.put("projectId", manifest.projectId().value());
        node.put("sourceRevision", manifest.sourceRevision());
        node.put("generatedAt", manifest.generatedAt().toString());
        node.set("coverage", array(manifest.coverage(), coverage -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("analyzerId", coverage.analyzerId());
            item.put("status", coverage.status().name());
            item.put("scopeUri", coverage.scopeUri());
            item.put("summary", coverage.summary());
            return item;
        }));
        return node;
    }

    private ObjectNode node(ArchitectureAsset asset) {
        ObjectNode node = metadataNode(asset.metadata());
        node.put("kind", asset.kind().name());
        switch (asset) {
            case BusinessConcept concept -> node.put("responsibility", concept.responsibility());
            case DomainConstraint constraint -> {
                node.put("subjectId", constraint.subjectId().value());
                node.put("constraintKind", constraint.constraintKind().name());
                node.put("enforcementLevel", constraint.enforcementLevel().name());
                node.put("statement", constraint.statement());
            }
            case DomainEvent event -> {
                node.put("businessFact", event.businessFact());
                node.set("payload", array(event.payload(), field -> {
                    ObjectNode item = mapper.createObjectNode();
                    item.put("name", field.name());
                    item.put("role", field.role().name());
                    item.put("businessMeaning", field.businessMeaning());
                    return item;
                }));
            }
            case ApplicationUseCase useCase -> node.put("purpose", useCase.purpose());
            case Projection projection -> node.put("purpose", projection.purpose());
            case ProcessManager processManager -> {
                node.put("responsibility", processManager.responsibility());
                node.put("completionCondition", processManager.completionCondition());
            }
            case EndpointOperation endpoint -> node.put("purpose", endpoint.purpose());
            case CodeElement code -> {
                node.put("elementKind", code.elementKind().name());
                node.put("qualifiedName", code.qualifiedName());
                node.set("locator", locatorNode(code.locator()));
            }
            case DataTarget target -> node.put("targetKind", target.targetKind().name());
            case ExternalBoundary boundary -> {
                node.put("boundaryKind", boundary.boundaryKind().name());
                node.put("responsibility", boundary.responsibility());
            }
            case SemanticValue value -> {
                node.put("semanticCategory", value.category().name());
                node.put("javaRepresentation", value.javaRepresentation());
                node.put("cardinality", value.cardinality().name());
                node.put("nullability", value.nullability().name());
                node.put("trustLevel", value.trustLevel().name());
                node.put("sensitivity", value.sensitivity().name());
                node.put("lifecycleState", value.lifecycleState());
            }
            case OperationContract operation -> {
                node.put("operationKind", operation.operationKind().name());
                node.put("javaSignature", operation.javaSignature());
                node.set("locator", locatorNode(operation.locator()));
                node.set("inputs", array(operation.inputs(), this::operationInputNode));
                node.set("outcomes", array(operation.outcomes(), this::operationOutcomeNode));
                node.set("failures", array(operation.failures(), this::operationFailureNode));
            }
            case ComponentDefinition component -> {
                node.put("componentKind", component.componentKind().name());
                node.put("beanName", component.beanName());
                node.put("declaredType", component.declaredType());
                node.put("candidateType", component.candidateType());
                node.put("scope", component.scope().name());
                node.put("primary", component.primary());
                node.set("qualifiers", stringArray(component.qualifiers()));
                node.set("activationCondition", conditionNode(component.activationCondition()));
            }
            case InjectionPoint injection -> {
                node.put("ownerComponentId", injection.ownerComponentId().value());
                node.put("requestedType", injection.requestedType());
                node.put("injectionKind", injection.injectionKind().name());
                node.put("injectionCardinality", injection.cardinality().name());
                node.put("required", injection.required());
                node.set("qualifiers", stringArray(injection.qualifiers()));
            }
            case ConfigurationProperty property -> {
                node.put("propertyKey", property.propertyKey());
                node.put("declaredDefault", property.declaredDefault());
                node.put("sensitivity", property.sensitivity().name());
            }
            case ConfigurationScenario scenario -> {
                node.set("activeProfiles", stringArray(scenario.activeProfiles()));
                ObjectNode properties = node.putObject("properties");
                scenario.properties().forEach((key, value) -> {
                    ObjectNode property = properties.putObject(key);
                    property.put("provenance", value.provenance());
                    property.put("value", value.value());
                    property.put("redacted", value.redacted());
                });
                node.set("classpathCapabilities", stringArray(scenario.classpathCapabilities()));
                node.put("classpathCompleteness", scenario.classpathCapabilityCompleteness().name());
                ObjectNode context = node.putObject("applicationContext");
                context.put("id", scenario.applicationContext().contextId());
                context.set("entryPointTypes", stringArray(scenario.applicationContext().entryPointTypes()));
                context.set("rootModuleCoordinates", stringArray(scenario.applicationContext().rootModuleCoordinates()));
                context.set("sourcePathPrefixes", stringArray(scenario.applicationContext().sourcePathPrefixes()));
                context.set("componentScanPackages", stringArray(scenario.applicationContext().componentScanPackages()));
                context.put("beanDefinitionOverridePolicy", scenario.applicationContext().beanDefinitionOverridePolicy().name());
                ObjectNode overrides = context.putObject("explicitBeanOverrides");
                scenario.applicationContext().explicitBeanOverrides().forEach(overrides::put);
            }
            default -> throw new IllegalArgumentException("未知 Atlas 节点实现类型: " + asset.getClass().getName());
        }
        return node;
    }

    private ObjectNode operationInputNode(OperationInput input) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", input.name());
        node.put("javaType", input.javaType());
        node.put("role", input.role().name());
        node.put("implicit", input.implicit());
        node.put("nullability", input.nullability().name());
        node.put("description", input.description());
        return node;
    }

    private ObjectNode operationOutcomeNode(OperationOutcome outcome) {
        ObjectNode node = mapper.createObjectNode();
        node.put("kind", outcome.kind().name());
        node.put("name", outcome.name());
        node.put("javaType", outcome.javaType());
        node.put("condition", outcome.condition());
        node.put("description", outcome.description());
        return node;
    }

    private ObjectNode operationFailureNode(OperationFailure failure) {
        ObjectNode node = mapper.createObjectNode();
        node.put("exceptionType", failure.exceptionType());
        node.put("condition", failure.condition());
        node.put("description", failure.description());
        return node;
    }

    private ObjectNode conditionNode(ConditionExpression condition) {
        ObjectNode node = mapper.createObjectNode();
        node.put("canonicalForm", condition.canonicalForm());
        node.put("status", condition.status().name());
        node.set("terms", array(condition.terms(), term -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("kind", term.kind());
            item.put("subject", term.subject());
            item.put("operator", term.operator());
            item.put("expectedValue", term.expectedValue());
            return item;
        }));
        return node;
    }

    private ArrayNode stringArray(List<String> values) {
        return array(values, mapper.getNodeFactory()::textNode);
    }

    private ObjectNode metadataNode(AssetMetadata metadata) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", metadata.id().value());
        node.put("name", metadata.name());
        node.set("attributes", valuesNode(metadata.attributes()));
        node.set("evidence", array(metadata.evidence(), this::evidenceNode));
        return node;
    }

    private ObjectNode relationNode(AtlasRelation relation) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", relation.id().value());
        node.put("kind", relation.kind().name());
        node.put("sourceId", relation.sourceId().value());
        node.put("targetId", relation.targetId().value());
        node.set("attributes", valuesNode(relation.attributes()));
        node.set("evidence", array(relation.evidence(), this::evidenceNode));
        return node;
    }

    private ObjectNode factsNode(FactBundle facts) {
        ObjectNode node = mapper.createObjectNode();
        node.set("facts", array(facts.facts(), fact -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", fact.id().value());
            item.put("analyzerId", fact.analyzerId());
            item.put("kind", fact.kind().name());
            item.put("subject", fact.subject());
            item.set("attributes", valuesNode(fact.attributes()));
            item.set("evidence", array(fact.evidence(), this::evidenceNode));
            return item;
        }));
        node.set("relations", array(facts.relations(), relation -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", relation.id().value());
            item.put("kind", relation.kind().name());
            item.put("sourceFactId", relation.sourceFactId().value());
            item.put("targetFactId", relation.targetFactId().value());
            item.set("attributes", valuesNode(relation.attributes()));
            item.set("evidence", array(relation.evidence(), this::evidenceNode));
            return item;
        }));
        return node;
    }

    private ObjectNode intentsNode(IntentBundle intents) {
        ObjectNode node = mapper.createObjectNode();
        node.set("declarations", array(intents.declarations(), declaration -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", declaration.id().value());
            item.put("annotationType", declaration.annotationType());
            item.put("assetKind", declaration.assetKind().name());
            ObjectNode target = item.putObject("target");
            target.put("kind", declaration.target().kind());
            target.put("binaryName", declaration.target().binaryName());
            target.put("memberDescriptor", declaration.target().memberDescriptor());
            item.set("attributes", valuesNode(declaration.attributes()));
            item.set("evidence", evidenceNode(declaration.evidence()));
            return item;
        }));
        return node;
    }

    private ObjectNode contentNode(ContentBundle content) {
        ObjectNode node = mapper.createObjectNode();
        node.set("entries", array(content.entries(), entry -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", entry.id().value());
            item.put("assetId", entry.assetId().value());
            item.put("audience", entry.audience().name());
            item.put("title", entry.title());
            item.put("explanation", entry.explanation());
            item.put("actionWhenViolated", entry.actionWhenViolated());
            item.set("evidence", evidenceNode(entry.evidence()));
            return item;
        }));
        return node;
    }

    private ObjectNode runtimeNode(RuntimeOverlay runtime) {
        ObjectNode node = mapper.createObjectNode();
        node.put("capturedAt", runtime.capturedAt().toString());
        node.set("observations", array(runtime.observations(), observation -> {
            ObjectNode item = mapper.createObjectNode();
            item.put("id", observation.id().value());
            item.put("subjectId", observation.subjectId().value());
            item.put("observedAt", observation.observedAt().toString());
            item.put("observationType", observation.observationType());
            item.set("attributes", valuesNode(observation.attributes()));
            item.set("evidence", evidenceNode(observation.evidence()));
            return item;
        }));
        return node;
    }

    private ObjectNode diagnosticNode(AtlasDiagnostic diagnostic) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", diagnostic.id().value());
        node.put("severity", diagnostic.severity().name());
        node.put("code", diagnostic.code());
        node.put("message", diagnostic.message());
        diagnostic.locator().ifPresent(locator -> node.set("locator", locatorNode(locator)));
        return node;
    }

    private ObjectNode evidenceNode(Evidence evidence) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", evidence.id().value());
        node.put("origin", evidence.origin().name());
        node.put("claim", evidence.claim().name());
        node.put("status", evidence.status().name());
        node.set("locator", locatorNode(evidence.locator()));
        node.put("sourceRevision", evidence.sourceRevision());
        node.put("visibility", evidence.visibility().name());
        node.put("analyzerId", evidence.analyzerId());
        node.put("summary", evidence.summary());
        return node;
    }

    private ObjectNode locatorNode(SourceLocator locator) {
        ObjectNode node = mapper.createObjectNode();
        node.put("uri", locator.uri());
        node.put("symbol", locator.symbol());
        ObjectNode range = node.putObject("range");
        range.put("startLine", locator.range().startLine());
        range.put("startColumn", locator.range().startColumn());
        range.put("endLine", locator.range().endLine());
        range.put("endColumn", locator.range().endColumn());
        return node;
    }

    private ObjectNode valuesNode(Map<String, AtlasValue> values) {
        ObjectNode node = mapper.createObjectNode();
        values.forEach((key, value) -> node.set(key, valueNode(value)));
        return node;
    }

    private JsonNode valueNode(AtlasValue value) {
        return switch (value) {
            case AtlasValue.Text text -> mapper.getNodeFactory().textNode(text.value());
            case AtlasValue.Decimal decimal -> mapper.getNodeFactory().numberNode(decimal.value());
            case AtlasValue.Bool bool -> mapper.getNodeFactory().booleanNode(bool.value());
            case AtlasValue.Values values -> array(values.values(), this::valueNode);
            case AtlasValue.ObjectValue object -> valuesNode(object.entries());
        };
    }

    private <T> ArrayNode array(List<T> values, java.util.function.Function<T, JsonNode> converter) {
        ArrayNode array = mapper.createArrayNode();
        values.forEach(value -> array.add(converter.apply(value)));
        return array;
    }

    private AtlasPackage readPackage(JsonNode root) {
        AtlasPackageManifest manifest = readManifest(root.path("manifest"));
        JsonNode graph = root.path("designAtlas");
        AtlasGraph designAtlas = new AtlasGraph(list(graph.path("nodes"), this::readNode), list(graph.path("relations"), this::readRelation));
        FactBundle facts = readFacts(root.path("facts"));
        IntentBundle intents = new IntentBundle(list(root.path("intents").path("declarations"), this::readIntent));
        ContentBundle content = new ContentBundle(list(root.path("content").path("entries"), this::readContent));
        Optional<RuntimeOverlay> runtime = root.has("runtimeOverlay") ? Optional.of(readRuntime(root.path("runtimeOverlay"))) : Optional.empty();
        return new AtlasPackage(manifest, designAtlas, facts, intents, content, runtime, list(root.path("diagnostics"), this::readDiagnostic));
    }

    private AtlasPackageManifest readManifest(JsonNode node) {
        return new AtlasPackageManifest(text(node, "schemaVersion"), id(node, "packageId"), id(node, "projectId"), text(node, "sourceRevision"),
                Instant.parse(text(node, "generatedAt")), list(node.path("coverage"), coverage -> new CoverageReport(
                text(coverage, "analyzerId"), CapabilityStatus.valueOf(text(coverage, "status")), text(coverage, "scopeUri"), textOrEmpty(coverage, "summary"))));
    }

    private ArchitectureAsset readNode(JsonNode node) {
        AssetMetadata metadata = readMetadata(node);
        return switch (AssetKind.valueOf(text(node, "kind"))) {
            case BUSINESS_CONCEPT -> new BusinessConcept(metadata, text(node, "responsibility"));
            case DOMAIN_CONSTRAINT -> new DomainConstraint(metadata, id(node, "subjectId"), DomainConstraintKind.valueOf(text(node, "constraintKind")),
                    EnforcementLevel.valueOf(text(node, "enforcementLevel")), text(node, "statement"));
            case DOMAIN_EVENT -> new DomainEvent(metadata, text(node, "businessFact"), list(node.path("payload"), field -> new EventField(
                    text(field, "name"), EventFieldRole.valueOf(text(field, "role")), textOrEmpty(field, "businessMeaning"))));
            case APPLICATION_USE_CASE -> new ApplicationUseCase(metadata, text(node, "purpose"));
            case PROJECTION -> new Projection(metadata, text(node, "purpose"));
            case PROCESS_MANAGER -> new ProcessManager(metadata, text(node, "responsibility"), textOrEmpty(node, "completionCondition"));
            case ENDPOINT_OPERATION -> new EndpointOperation(metadata, text(node, "purpose"));
            case CODE_ELEMENT -> new CodeElement(metadata, CodeElementKind.valueOf(text(node, "elementKind")), text(node, "qualifiedName"), readLocator(node.path("locator")));
            case DATA_TARGET -> new DataTarget(metadata, DataTargetKind.valueOf(text(node, "targetKind")));
            case EXTERNAL_BOUNDARY -> new ExternalBoundary(metadata, BoundaryKind.valueOf(text(node, "boundaryKind")), text(node, "responsibility"));
            case SEMANTIC_VALUE -> new SemanticValue(metadata,
                    SemanticValueCategory.valueOf(text(node, "semanticCategory")), text(node, "javaRepresentation"),
                    ValueCardinality.valueOf(text(node, "cardinality")), ValueNullability.valueOf(text(node, "nullability")),
                    TrustLevel.valueOf(text(node, "trustLevel")), DataSensitivity.valueOf(text(node, "sensitivity")), text(node, "lifecycleState"));
            case OPERATION_CONTRACT -> new OperationContract(metadata, OperationKind.valueOf(text(node, "operationKind")),
                    text(node, "javaSignature"), readLocator(node.path("locator")),
                    list(node.path("inputs"), input -> new OperationInput(text(input, "name"), text(input, "javaType"),
                            OperationInputRole.valueOf(text(input, "role")), input.path("implicit").asBoolean(false),
                            ValueNullability.valueOf(text(input, "nullability")), textOrEmpty(input, "description"))),
                    list(node.path("outcomes"), outcome -> new OperationOutcome(OperationOutcomeKind.valueOf(text(outcome, "kind")),
                            text(outcome, "name"), text(outcome, "javaType"), textOrEmpty(outcome, "condition"),
                            textOrEmpty(outcome, "description"))),
                    list(node.path("failures"), failure -> new OperationFailure(text(failure, "exceptionType"),
                            textOrEmpty(failure, "condition"), textOrEmpty(failure, "description"))));
            case COMPONENT_DEFINITION -> new ComponentDefinition(metadata, ComponentKind.valueOf(text(node, "componentKind")),
                    text(node, "beanName"), text(node, "declaredType"), text(node, "candidateType"),
                    ComponentScope.valueOf(text(node, "scope")), node.path("primary").asBoolean(false),
                    stringList(node.path("qualifiers")), readCondition(node.path("activationCondition")));
            case INJECTION_POINT -> new InjectionPoint(metadata, id(node, "ownerComponentId"), text(node, "requestedType"),
                    InjectionKind.valueOf(text(node, "injectionKind")), InjectionCardinality.valueOf(text(node, "injectionCardinality")),
                    node.path("required").asBoolean(false), stringList(node.path("qualifiers")));
            case CONFIGURATION_PROPERTY -> new ConfigurationProperty(metadata, text(node, "propertyKey"), textOrEmpty(node, "declaredDefault"),
                    DataSensitivity.valueOf(text(node, "sensitivity")));
            case CONFIGURATION_SCENARIO -> new ConfigurationScenario(metadata, stringList(node.path("activeProfiles")),
                    scenarioProperties(node.path("properties")), stringList(node.path("classpathCapabilities")),
                    classpathCompleteness(node.path("classpathCompleteness")),
                    applicationContext(node.path("applicationContext")));
        };
    }

    private ClasspathCapabilityCompleteness classpathCompleteness(JsonNode node) {
        return node.isMissingNode() || node.isNull()
                ? ClasspathCapabilityCompleteness.PARTIAL
                : ClasspathCapabilityCompleteness.valueOf(textNode(node, "classpathCompleteness"));
    }

    private static String textNode(JsonNode node, String label) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("缺少非空文本字段: " + label);
        }
        return node.asText().trim();
    }

    private ApplicationContextScope applicationContext(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return ApplicationContextScope.global();
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        JsonNode overrideNode = node.path("explicitBeanOverrides");
        if (overrideNode.isObject()) {
            overrideNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                    throw new IllegalArgumentException("场景 explicitBeanOverride 必须为非空文本: " + entry.getKey());
                }
                overrides.put(entry.getKey(), entry.getValue().asText().trim());
            });
        }
        return new ApplicationContextScope(
                text(node, "id"),
                stringList(node.path("entryPointTypes")),
                stringList(node.path("rootModuleCoordinates")),
                stringList(node.path("sourcePathPrefixes")),
                stringList(node.path("componentScanPackages")),
                BeanDefinitionOverridePolicy.valueOf(text(node, "beanDefinitionOverridePolicy")),
                overrides);
    }

    private ConditionExpression readCondition(JsonNode node) {
        return new ConditionExpression(text(node, "canonicalForm"), ConditionStatus.valueOf(text(node, "status")),
                list(node.path("terms"), term -> new ConditionTerm(text(term, "kind"), text(term, "subject"),
                        text(term, "operator"), textOrEmpty(term, "expectedValue"))));
    }

    private Map<String, ScenarioProperty> scenarioProperties(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("场景 properties 必须是对象");
        }
        Map<String, ScenarioProperty> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), new ScenarioProperty(
                text(entry.getValue(), "provenance"), textOrEmpty(entry.getValue(), "value"),
                entry.getValue().path("redacted").asBoolean(false))));
        return Map.copyOf(values);
    }

    private static List<String> stringList(JsonNode node) {
        return list(node, value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("预期文本数组元素");
            }
            return value.asText();
        });
    }

    private AssetMetadata readMetadata(JsonNode node) {
        return new AssetMetadata(id(node, "id"), text(node, "name"), values(node.path("attributes")), list(node.path("evidence"), this::readEvidence));
    }

    private AtlasRelation readRelation(JsonNode node) {
        return new AtlasRelation(id(node, "id"), RelationKind.valueOf(text(node, "kind")), id(node, "sourceId"), id(node, "targetId"),
                values(node.path("attributes")), list(node.path("evidence"), this::readEvidence));
    }

    private FactBundle readFacts(JsonNode node) {
        List<SourceFact> facts = list(node.path("facts"), fact -> new SourceFact(id(fact, "id"), text(fact, "analyzerId"),
                FactKind.valueOf(text(fact, "kind")), text(fact, "subject"), values(fact.path("attributes")), list(fact.path("evidence"), this::readEvidence)));
        List<FactRelation> relations = list(node.path("relations"), relation -> new FactRelation(id(relation, "id"),
                RelationKind.valueOf(text(relation, "kind")), id(relation, "sourceFactId"), id(relation, "targetFactId"),
                values(relation.path("attributes")), list(relation.path("evidence"), this::readEvidence)));
        return new FactBundle(facts, relations);
    }

    private IntentDeclaration readIntent(JsonNode node) {
        JsonNode target = node.path("target");
        return new IntentDeclaration(id(node, "id"), text(node, "annotationType"), AssetKind.valueOf(text(node, "assetKind")),
                new IntentTarget(text(target, "kind"), text(target, "binaryName"), textOrEmpty(target, "memberDescriptor")),
                values(node.path("attributes")), readEvidence(node.path("evidence")));
    }

    private ContentEntry readContent(JsonNode node) {
        return new ContentEntry(id(node, "id"), id(node, "assetId"), Audience.valueOf(text(node, "audience")), text(node, "title"),
                text(node, "explanation"), textOrEmpty(node, "actionWhenViolated"), readEvidence(node.path("evidence")));
    }

    private RuntimeOverlay readRuntime(JsonNode node) {
        return new RuntimeOverlay(Instant.parse(text(node, "capturedAt")), list(node.path("observations"), observation -> new RuntimeObservation(
                id(observation, "id"), id(observation, "subjectId"), Instant.parse(text(observation, "observedAt")), text(observation, "observationType"),
                values(observation.path("attributes")), readEvidence(observation.path("evidence")))));
    }

    private AtlasDiagnostic readDiagnostic(JsonNode node) {
        return new AtlasDiagnostic(id(node, "id"), DiagnosticSeverity.valueOf(text(node, "severity")), text(node, "code"), text(node, "message"),
                node.has("locator") ? Optional.of(readLocator(node.path("locator"))) : Optional.empty());
    }

    private Evidence readEvidence(JsonNode node) {
        return new Evidence(id(node, "id"), EvidenceOrigin.valueOf(text(node, "origin")), EvidenceClaim.valueOf(text(node, "claim")),
                EvidenceStatus.valueOf(text(node, "status")), readLocator(node.path("locator")), text(node, "sourceRevision"),
                EvidenceVisibility.valueOf(text(node, "visibility")), text(node, "analyzerId"), textOrEmpty(node, "summary"));
    }

    private SourceLocator readLocator(JsonNode node) {
        JsonNode range = node.path("range");
        return new SourceLocator(text(node, "uri"), textOrEmpty(node, "symbol"), new SourceRange(
                integer(range, "startLine"), integer(range, "startColumn"), integer(range, "endLine"), integer(range, "endColumn")));
    }

    private static Map<String, AtlasValue> values(JsonNode node) {
        return ConventionProfileJsonReader.values(node);
    }

    private static AtlasId id(JsonNode node, String field) {
        return AtlasId.of(text(node, field));
    }

    private static String text(JsonNode node, String field) {
        return ConventionProfileJsonReader.text(node, field);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        return ConventionProfileJsonReader.textOrEmpty(node, field);
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isInt()) {
            throw new IllegalArgumentException("缺少整数值: " + field);
        }
        return value.asInt();
    }

    private static <T> List<T> list(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        if (!array.isArray()) {
            throw new IllegalArgumentException("预期 JSON 数组");
        }
        List<T> values = new ArrayList<>();
        array.forEach(node -> values.add(mapper.apply(node)));
        return List.copyOf(values);
    }
}
