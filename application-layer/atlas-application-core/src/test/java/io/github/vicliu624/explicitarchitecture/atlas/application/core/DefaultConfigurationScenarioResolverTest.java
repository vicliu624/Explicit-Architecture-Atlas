package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackageManifest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BeanDefinitionOverridePolicy;
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
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionCardinality;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.InjectionPoint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioInvocationStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigurationScenarioResolverTest {

    @Test
    void resolvesConditionalBeanAndItsInjectionForAnExplicitScenario() {
        Evidence evidence = evidence();
        ComponentDefinition service = component("service", "PaymentService", false, ConditionExpression.always(), evidence);
        ComponentDefinition remote = component("remoteGateway", "PaymentGateway", true,
                propertyCondition("payment.gateway", "remote"), evidence);
        ComponentDefinition local = component("localGateway", "PaymentGateway", false,
                propertyCondition("payment.gateway", "local"), evidence);
        InjectionPoint injection = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:gateway"), "gateway", Map.of(), List.of(evidence)),
                service.id(), "PaymentGateway", InjectionKind.CONSTRUCTOR, InjectionCardinality.SINGLE, true, List.of());
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest("1.0", AtlasId.of("package:scenario"), AtlasId.of("project:scenario"), "test",
                        Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(service, remote, local, injection), List.of()),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:prod"), "production", Map.of(), List.of(evidence)),
                List.of("prod"), Map.of("payment.gateway", new ScenarioProperty("application-prod.yml", "remote", false)), List.of());

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        assertTrue(resolution.components().stream().anyMatch(status -> status.componentId().equals(remote.id())
                && status.status() == ConditionStatus.ACTIVE));
        assertTrue(resolution.components().stream().anyMatch(status -> status.componentId().equals(local.id())
                && status.status() == ConditionStatus.EXCLUDED));
        assertEquals(List.of(remote.id()), resolution.injections().getFirst().selected());
    }

    @Test
    void evaluatesProfileExpressionsAndSpringPropertyMissingSemanticsWithoutGuessingConditionCycles() {
        Evidence evidence = evidence();
        ComponentDefinition profileAndProperty = component("gateway", "PaymentGateway", false,
                new ConditionExpression("(prod | staging) AND payment.enabled != false or missing", ConditionStatus.POSSIBLE,
                        List.of(
                                new ConditionTerm("PROFILE", "activeProfiles", "MATCHES_ANY", "prod|staging"),
                                new ConditionTerm("PROPERTY", "payment.enabled", "NOT_EQUALS_IGNORE_CASE_OR_MISSING", "false"))), evidence);
        ComponentDefinition firstMissing = component("firstDefault", "FallbackGateway", false,
                beanCondition("FallbackGateway", "ABSENT"), evidence);
        ComponentDefinition secondMissing = component("secondDefault", "FallbackGateway", false,
                beanCondition("FallbackGateway", "ABSENT"), evidence);
        AtlasPackage atlasPackage = packageOf(List.of(profileAndProperty, firstMissing, secondMissing));
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:staging"), "staging", Map.of(), List.of(evidence)),
                List.of("staging"), Map.of(), List.of());

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        assertTrue(resolution.components().stream().anyMatch(status -> status.componentId().equals(profileAndProperty.id())
                && status.status() == ConditionStatus.ACTIVE));
        assertTrue(resolution.components().stream().filter(status -> status.componentId().equals(firstMissing.id())
                        || status.componentId().equals(secondMissing.id()))
                .allMatch(status -> status.status() == ConditionStatus.UNRESOLVED));
    }

    @Test
    void keepsOptionalAndProviderInjectionDistinctFromEagerSingleBeanSelection() {
        Evidence evidence = evidence();
        ComponentDefinition service = component("service", "PaymentService", false, ConditionExpression.always(), evidence);
        ComponentDefinition gateway = component("gateway", "PaymentGateway", false, ConditionExpression.always(), evidence);
        InjectionPoint optional = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:optional"), "optionalGateway", Map.of(), List.of(evidence)),
                service.id(), "MissingGateway", InjectionKind.CONSTRUCTOR, InjectionCardinality.OPTIONAL, true, List.of());
        InjectionPoint provider = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:provider"), "gatewayProvider", Map.of(), List.of(evidence)),
                service.id(), "PaymentGateway", InjectionKind.OBJECT_PROVIDER, InjectionCardinality.PROVIDER, true, List.of());
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest("1.0", AtlasId.of("package:provider"), AtlasId.of("project:scenario"), "test", Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(service, gateway, optional, provider), List.of()),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:provider"), "provider", Map.of(), List.of(evidence)), List.of(), Map.of(), List.of());

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        var optionalResolution = resolution.injections().stream().filter(item -> item.injectionPointId().equals(optional.id())).findFirst().orElseThrow();
        var providerResolution = resolution.injections().stream().filter(item -> item.injectionPointId().equals(provider.id())).findFirst().orElseThrow();
        assertEquals(ConditionStatus.ACTIVE, optionalResolution.status());
        assertTrue(optionalResolution.selected().isEmpty());
        assertEquals(ConditionStatus.ACTIVE, providerResolution.status());
        assertEquals(List.of(gateway.id()), providerResolution.candidates());
        assertTrue(providerResolution.selected().isEmpty());
    }

    @Test
    void treatsUniquePrimaryAsASingleConditionalCandidate() {
        Evidence evidence = evidence();
        ComponentDefinition defaultGateway = component("defaultGateway", "PaymentGateway", false, ConditionExpression.always(), evidence);
        ComponentDefinition preferredGateway = component("preferredGateway", "PaymentGateway", true, ConditionExpression.always(), evidence);
        ComponentDefinition feature = component("feature", "FeatureService", false,
                beanCondition("PaymentGateway", "SINGLE_CANDIDATE"), evidence);
        AtlasPackage atlasPackage = packageOf(List.of(defaultGateway, preferredGateway, feature));
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:primary"), "primary", Map.of(), List.of(evidence)), List.of(), Map.of(), List.of());

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        assertTrue(resolution.components().stream().anyMatch(status -> status.componentId().equals(feature.id())
                && status.status() == ConditionStatus.ACTIVE));
    }

    @Test
    void resolvesAnInterfaceCallThroughItsMatchedInjectionAndScenarioSelectedBean() {
        Evidence evidence = evidence();
        ComponentDefinition service = component("service", "example.PaymentService", false, ConditionExpression.always(), evidence);
        ComponentDefinition remote = component("remoteGateway", "example.PaymentGateway", "example.RemoteGateway", false,
                propertyCondition("payment.gateway", "remote"), evidence);
        ComponentDefinition local = component("localGateway", "example.PaymentGateway", "example.LocalGateway", false,
                propertyCondition("payment.gateway", "local"), evidence);
        InjectionPoint gateway = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:gateway"), "gateway", Map.of(), List.of(evidence)),
                service.id(), "example.PaymentGateway", InjectionKind.CONSTRUCTOR, InjectionCardinality.SINGLE, true, List.of());
        CodeElement caller = code("code:payment-service-submit", "example.PaymentService#submit(example.Command)", evidence);
        CodeElement staticTarget = code("code:gateway-charge", "example.PaymentGateway#charge(example.Command)", evidence);
        CodeElement remoteImplementation = code("code:remote-charge", "example.RemoteGateway#charge(example.Command)", evidence);
        AtlasRelation call = new AtlasRelation(AtlasId.of("relation:payment-service-calls-gateway"), RelationKind.CALLS,
                caller.id(), staticTarget.id(), Map.of("calleeExpression", AtlasValue.text("gateway.charge")), List.of(evidence));
        AtlasPackage atlasPackage = new AtlasPackage(
                new AtlasPackageManifest("1.0", AtlasId.of("package:effective-call"), AtlasId.of("project:scenario"), "test",
                        Instant.EPOCH, List.of()),
                new AtlasGraph(List.of(service, remote, local, gateway, caller, staticTarget, remoteImplementation), List.of(call)),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:remote"), "remote", Map.of(), List.of(evidence)), List.of(),
                Map.of("payment.gateway", new ScenarioProperty("application.yml", "remote", false)), List.of());

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        var invocation = resolution.invocations().getFirst();
        assertEquals(ScenarioInvocationStatus.RESOLVED, invocation.status(), String.join("；", invocation.reasons()));
        assertEquals(List.of(gateway.id()), invocation.receiverInjectionPointIds());
        assertEquals(List.of(remote.id()), invocation.selectedComponents());
        assertEquals(List.of(remoteImplementation.id()), invocation.effectiveTargetCodeIds());
        assertFalse(invocation.effectiveTargetCodeIds().contains(staticTarget.id()));
    }

    @Test
    void resolvesCandidatesOnlyInsideTheDeclaredApplicationContextBoundary() {
        Evidence appEvidence = evidence("ui-layer/app-backend/src/main/java/example/AppConfiguration.java");
        Evidence readEvidence = evidence("ui-layer/read-backend/src/main/java/example/ReadConfiguration.java");
        ComponentDefinition service = component("service", "example.PaymentService", false, ConditionExpression.always(), appEvidence);
        ComponentDefinition appGateway = component("appGateway", "example.PaymentGateway", "example.AppGateway", false,
                ConditionExpression.always(), appEvidence);
        ComponentDefinition readGateway = component("readGateway", "example.PaymentGateway", "example.ReadGateway", false,
                ConditionExpression.always(), readEvidence);
        InjectionPoint injection = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:scoped-gateway"), "gateway", Map.of(), List.of(appEvidence)),
                service.id(), "example.PaymentGateway", InjectionKind.CONSTRUCTOR, InjectionCardinality.SINGLE, true, List.of());
        AtlasPackage atlasPackage = packageOf(List.of(service, appGateway, readGateway, injection));
        ConfigurationScenario scenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:app"), "app", Map.of(), List.of(appEvidence)), List.of(), Map.of(), List.of(),
                new ApplicationContextScope("context:app-backend", List.of("example.AppApplication"),
                        List.of("ui-layer/app-backend"), List.of("example"), BeanDefinitionOverridePolicy.FORBID, Map.of()));

        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, scenario);

        assertEquals(List.of(appGateway.id()), resolution.injections().getFirst().selected());
        assertTrue(resolution.components().stream().anyMatch(status -> status.componentId().equals(readGateway.id())
                && status.status() == ConditionStatus.EXCLUDED && status.reason().contains("context:app-backend")));
    }

    @Test
    void treatsAllowedBeanOverrideAsUnresolvedUntilTheWinnerIsExplicitlyProven() {
        Evidence evidence = evidence("ui-layer/app-backend/src/main/java/example/Configuration.java");
        ComponentDefinition service = component("service", "example.PaymentService", false, ConditionExpression.always(), evidence);
        ComponentDefinition oldGateway = component("gateway", "example.PaymentGateway", "example.LegacyGateway", false,
                ConditionExpression.always(), evidence);
        ComponentDefinition replacement = new ComponentDefinition(
                new AssetMetadata(AtlasId.of("component:gateway-replacement"), "gateway", Map.of(), List.of(evidence)),
                ComponentKind.SERVICE, "gateway", "example.PaymentGateway", "example.CurrentGateway", ComponentScope.SINGLETON,
                false, List.of(), ConditionExpression.always());
        InjectionPoint injection = new InjectionPoint(
                new AssetMetadata(AtlasId.of("injection:override-gateway"), "gateway", Map.of(), List.of(evidence)),
                service.id(), "example.PaymentGateway", InjectionKind.CONSTRUCTOR, InjectionCardinality.SINGLE, true, List.of());
        AtlasPackage atlasPackage = packageOf(List.of(service, oldGateway, replacement, injection));
        ApplicationContextScope unresolvedScope = new ApplicationContextScope("context:app", List.of(),
                List.of("ui-layer/app-backend"), List.of("example"), BeanDefinitionOverridePolicy.ALLOW, Map.of());
        ConfigurationScenario unresolvedScenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:override-unresolved"), "unresolved", Map.of(), List.of(evidence)),
                List.of(), Map.of(), List.of(), unresolvedScope);

        var unresolved = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, unresolvedScenario);

        assertTrue(unresolved.components().stream().filter(status -> status.componentId().equals(oldGateway.id())
                        || status.componentId().equals(replacement.id()))
                .allMatch(status -> status.status() == ConditionStatus.UNRESOLVED));
        assertTrue(unresolved.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BEAN_DEFINITION_OVERRIDE_UNRESOLVED")));

        ApplicationContextScope explicitScope = new ApplicationContextScope("context:app", List.of(),
                List.of("ui-layer/app-backend"), List.of("example"), BeanDefinitionOverridePolicy.ALLOW,
                Map.of("gateway", "example.CurrentGateway"));
        ConfigurationScenario explicitScenario = new ConfigurationScenario(
                new AssetMetadata(AtlasId.of("scenario:override-explicit"), "explicit", Map.of(), List.of(evidence)),
                List.of(), Map.of(), List.of(), explicitScope);

        var explicit = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, explicitScenario);

        assertEquals(List.of(replacement.id()), explicit.injections().getFirst().selected());
        assertTrue(explicit.components().stream().anyMatch(status -> status.componentId().equals(oldGateway.id())
                && status.status() == ConditionStatus.EXCLUDED));
        assertTrue(explicit.components().stream().anyMatch(status -> status.componentId().equals(replacement.id())
                && status.status() == ConditionStatus.ACTIVE));
    }

    private static ComponentDefinition component(
            String beanName,
            String type,
            boolean primary,
            ConditionExpression condition,
            Evidence evidence) {
        return component(beanName, type, type, primary, condition, evidence);
    }

    private static ComponentDefinition component(
            String beanName,
            String declaredType,
            String candidateType,
            boolean primary,
            ConditionExpression condition,
            Evidence evidence) {
        return new ComponentDefinition(
                new AssetMetadata(AtlasId.of("component:" + beanName), beanName, Map.of(), List.of(evidence)),
                ComponentKind.SERVICE, beanName, declaredType, candidateType, ComponentScope.SINGLETON, primary, List.of(), condition);
    }

    private static CodeElement code(String id, String qualifiedName, Evidence evidence) {
        return new CodeElement(new AssetMetadata(AtlasId.of(id), qualifiedName, Map.of(), List.of(evidence)), CodeElementKind.METHOD,
                qualifiedName, evidence.locator());
    }

    private static ConditionExpression propertyCondition(String key, String value) {
        return new ConditionExpression("property(" + key + ")=" + value, ConditionStatus.POSSIBLE,
                List.of(new ConditionTerm("PROPERTY", key, "=", value)));
    }

    private static ConditionExpression beanCondition(String type, String operator) {
        return new ConditionExpression("bean(" + type + ") " + operator, ConditionStatus.POSSIBLE,
                List.of(new ConditionTerm("BEAN", type, operator, "")));
    }

    private static AtlasPackage packageOf(List<? extends ArchitectureAsset> assets) {
        return new AtlasPackage(
                new AtlasPackageManifest("1.0", AtlasId.of("package:scenario"), AtlasId.of("project:scenario"), "test",
                        Instant.EPOCH, List.of()),
                new AtlasGraph(assets.stream().map(asset -> (ArchitectureAsset) asset).toList(), List.of()),
                FactBundle.empty(), IntentBundle.empty(), ContentBundle.empty(), Optional.empty(), List.of());
    }

    private static Evidence evidence() {
        return evidence("src/main/java/example/Config.java");
    }

    private static Evidence evidence(String path) {
        return new Evidence(
                AtlasId.of("evidence:scenario"),
                EvidenceOrigin.SOURCE_CODE,
                EvidenceClaim.DERIVED,
                EvidenceStatus.VERIFIED,
                new SourceLocator("project://scenario/" + path, "example.Config", SourceRange.unknown()),
                "test",
                EvidenceVisibility.EXPERT_ONLY,
                "test",
                "test evidence");
    }
}
