package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
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
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ValueNullability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private static Map<String, String> properties(DefaultAtlasRoleViewQuery query, AtlasId packageId, AtlasId assetId) {
        return query.findExpertAssetDetail(packageId, assetId).orElseThrow().properties().stream()
                .collect(Collectors.toMap(property -> property.name(), property -> property.value()));
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
