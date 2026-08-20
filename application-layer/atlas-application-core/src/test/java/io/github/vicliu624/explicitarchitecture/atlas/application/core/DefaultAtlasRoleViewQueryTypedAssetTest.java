package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentDefinition;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ComponentScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionExpression;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConditionTerm;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataSensitivity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationContract;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInput;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationInputRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcome;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcomeKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValueCategory;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.domain.TrustLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueNullability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAtlasRoleViewQueryTypedAssetTest {

    @Test
    void exposesTypedOperationComponentAndScenarioFieldsWithoutLeakingRedactedValues() {
        Evidence evidence = evidence();
        OperationContract operation = new OperationContract(
                metadata("operation:submit", "submit", evidence),
                OperationKind.METHOD,
                "example.OrderService.submit(example.OrderCommand)",
                evidence.locator(),
                List.of(new OperationInput("command", "example.OrderCommand", OperationInputRole.COMMAND,
                        false, ValueNullability.REQUIRED, "待提交订单")),
                List.of(new OperationOutcome(OperationOutcomeKind.RETURN_VALUE, "result", "example.SubmitResult", "", "提交结果")),
                List.of());
        ComponentDefinition component = new ComponentDefinition(
                metadata("component:gateway", "gateway", evidence),
                ComponentKind.SERVICE, "gateway", "example.Gateway", "example.RemoteGateway", ComponentScope.SINGLETON,
                true, List.of("paymentGateway"),
                new ConditionExpression("profile(prod) AND property(payment.mode)=remote", ConditionStatus.POSSIBLE,
                        List.of(new ConditionTerm("PROFILE", "activeProfiles", "MATCHES", "prod"),
                                new ConditionTerm("PROPERTY", "payment.mode", "EQUALS", "remote"))));
        ConfigurationScenario scenario = new ConfigurationScenario(
                metadata("scenario:prod", "production", evidence),
                List.of("prod"),
                Map.of("payment.mode", new ScenarioProperty("application-prod.yml", "remote", false),
                        "payment.secret", new ScenarioProperty("vault", "will-not-survive", true)),
                List.of("spring-web", "postgresql"));
        AtlasId packageId = AtlasId.of("package:typed-view");
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest("1.0", packageId, AtlasId.of("project:typed-view"), "test", Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(operation, component, scenario), List.of()),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
        AtlasPackageQueryUseCase packages = requested -> packageId.equals(requested) ? Optional.of(atlasPackage) : Optional.empty();
        DefaultAtlasRoleViewQuery query = new DefaultAtlasRoleViewQuery(packages);

        Map<String, String> operationProperties = properties(query, packageId, operation.id());
        assertEquals("example.OrderCommand", operationProperties.get("operation.input[0].javaType"));
        assertEquals("COMMAND", operationProperties.get("operation.input[0].role"));
        assertEquals("example.SubmitResult", operationProperties.get("operation.outcome[0].javaType"));

        Map<String, String> componentProperties = properties(query, packageId, component.id());
        assertEquals("gateway", componentProperties.get("component.beanName"));
        assertEquals("profile(prod) AND property(payment.mode)=remote", componentProperties.get("component.activation.canonicalForm"));
        assertEquals("payment.mode", componentProperties.get("component.activation.term[1].subject"));

        Map<String, String> scenarioProperties = properties(query, packageId, scenario.id());
        assertEquals("<redacted>", scenarioProperties.get("scenario.property[payment.secret].value"));
        assertFalse(scenarioProperties.values().contains("will-not-survive"));
        assertTrue(scenarioProperties.containsKey("scenario.activeProfile[0]"));
    }

    @Test
    void exposesMethodParametersReturnValueAndProvenValueFlowsFromTheCodeElementDetail() {
        Evidence evidence = evidence();
        AtlasId packageId = AtlasId.of("package:function-contract-view");
        CodeElement code = new CodeElement(metadata("code:submit", "submit source", evidence), CodeElementKind.METHOD,
                "example.OrderService#submit(java.lang.String)", evidence.locator());
        OperationContract operation = new OperationContract(
                metadata("operation:submit", "submit", evidence), OperationKind.METHOD, code.qualifiedName(), evidence.locator(),
                List.of(new OperationInput("orderId", "java.lang.String", OperationInputRole.BUSINESS_INPUT,
                        false, ValueNullability.REQUIRED, "订单标识")),
                List.of(new OperationOutcome(OperationOutcomeKind.RETURN_VALUE, "return", "example.Order", "", "提交后的订单")),
                List.of());
        SemanticValue callerArgument = semanticValue("value:caller-order-id", "rawOrderId", "java.lang.String", Map.of());
        SemanticValue parameter = semanticValue("value:submit-order-id", "orderId", "java.lang.String",
                Map.of("parameterIndex", AtlasValue.decimal(BigDecimal.ZERO)));
        SemanticValue result = semanticValue("value:submit-return", "return", "example.Order", Map.of());
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest("1.0", packageId, AtlasId.of("project:function-contract-view"), "test", Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(code, operation, callerArgument, parameter, result), List.of(
                        relation("relation:represents", RelationKind.REPRESENTS, operation.id(), code.id(), evidence),
                        relation("relation:accepts", RelationKind.ACCEPTS, operation.id(), parameter.id(), evidence),
                        relation("relation:returns", RelationKind.RETURNS, operation.id(), result.id(), evidence),
                        relation("relation:argument-binds", RelationKind.ARGUMENT_BINDS_TO, callerArgument.id(), parameter.id(), evidence),
                        relation("relation:return-flow", RelationKind.RETURN_FLOWS_TO, parameter.id(), result.id(), evidence))),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
        DefaultAtlasRoleViewQuery query = new DefaultAtlasRoleViewQuery(requested ->
                packageId.equals(requested) ? Optional.of(atlasPackage) : Optional.empty());

        var contracts = query.findExpertAssetDetail(packageId, code.id()).orElseThrow().functionContracts();

        assertEquals(1, contracts.size());
        var contract = contracts.getFirst();
        assertEquals(code.qualifiedName(), contract.javaSignature());
        assertEquals("orderId", contract.inputs().getFirst().value().name());
        assertTrue(contract.inputs().getFirst().associations().stream()
                .anyMatch(relation -> relation.kind().equals("ARGUMENT_BINDS_TO")));
        assertEquals("example.Order", propertyMap(contract.outputs().getFirst().properties()).get("semantic.javaRepresentation"));
        assertTrue(contract.outputs().getFirst().associations().stream()
                .anyMatch(relation -> relation.kind().equals("RETURN_FLOWS_TO")));
    }

    private static Map<String, String> properties(DefaultAtlasRoleViewQuery query, AtlasId packageId, AtlasId assetId) {
        return query.findExpertAssetDetail(packageId, assetId).orElseThrow().properties().stream()
                .collect(Collectors.toMap(property -> property.name(), property -> property.value()));
    }

    private static SemanticValue semanticValue(String id, String name, String javaType, Map<String, AtlasValue> attributes) {
        return new SemanticValue(new AssetMetadata(AtlasId.of(id), name, attributes, List.of(evidence())),
                SemanticValueCategory.CUSTOM, javaType, ValueCardinality.SINGLE, ValueNullability.UNKNOWN,
                TrustLevel.UNKNOWN, DataSensitivity.UNKNOWN, "RAW");
    }

    private static AtlasRelation relation(String id, RelationKind kind, AtlasId source, AtlasId target, Evidence evidence) {
        return new AtlasRelation(AtlasId.of(id), kind, source, target, Map.of(), List.of(evidence));
    }

    private static Map<String, String> propertyMap(List<io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasProperty> properties) {
        return properties.stream().collect(Collectors.toMap(property -> property.name(), property -> property.value()));
    }

    private static AssetMetadata metadata(String id, String name, Evidence evidence) {
        return new AssetMetadata(AtlasId.of(id), name, Map.of(), List.of(evidence));
    }

    private static Evidence evidence() {
        return new Evidence(
                AtlasId.of("evidence:typed-view"), EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, EvidenceStatus.VERIFIED,
                new SourceLocator("project://fixture/src/main/java/example/OrderService.java", "example.OrderService", SourceRange.unknown()),
                "test", EvidenceVisibility.EXPERT_ONLY, "test", "typed view fixture");
    }
}
