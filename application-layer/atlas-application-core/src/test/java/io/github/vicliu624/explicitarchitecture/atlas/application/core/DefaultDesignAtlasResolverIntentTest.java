package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationProperty;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataSensitivity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentDeclaration;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationContract;
import io.github.vicliu624.explicitarchitecture.atlas.domain.OperationOutcomeKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SemanticValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDesignAtlasResolverIntentTest {

    @Test
    void indexesSourceProvenInterfaceHierarchyForPossibleBeanCandidates() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "spring-and-jvm-source");
        FactBundle facts = new FactBundle(List.of(
                fact("remote-type", FactKind.SOURCE_TYPE, "example.RemotePaymentGateway", Map.of(
                        "extendsType", AtlasValue.text(""),
                        "implementsTypes", AtlasValue.values(List.of(AtlasValue.text("example.PaymentGateway")))), source),
                fact("payment-service", FactKind.BEAN_DEFINITION, "example.PaymentService", Map.of(
                        "beanName", AtlasValue.text("paymentService"), "declaredType", AtlasValue.text("example.PaymentService"),
                        "candidateType", AtlasValue.text("example.PaymentService"), "componentKind", AtlasValue.text("SERVICE"),
                        "scope", AtlasValue.text("SINGLETON"), "primary", AtlasValue.bool(false)), source),
                fact("remote-gateway", FactKind.BEAN_DEFINITION, "example.RemotePaymentGateway", Map.of(
                        "beanName", AtlasValue.text("remotePaymentGateway"), "declaredType", AtlasValue.text("example.RemotePaymentGateway"),
                        "candidateType", AtlasValue.text("example.RemotePaymentGateway"), "componentKind", AtlasValue.text("SERVICE"),
                        "scope", AtlasValue.text("SINGLETON"), "primary", AtlasValue.bool(false)), source),
                fact("gateway-injection", FactKind.BEAN_INJECTION, "example.PaymentService#gateway", Map.of(
                        "ownerBeanName", AtlasValue.text("paymentService"), "memberName", AtlasValue.text("gateway"),
                        "requestedType", AtlasValue.text("example.PaymentGateway"), "injectionKind", AtlasValue.text("FIELD"),
                        "cardinality", AtlasValue.text("SINGLE"), "required", AtlasValue.bool(true),
                        "qualifiers", AtlasValue.values(List.of())), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        assertEquals(1, resolution.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.RESOLVES_TO).count());
    }

    @Test
    void indexesParameterOwnersAndMethodNamesWithoutCollapsingOverloadAmbiguity() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-source");
        String caller = "example.Forwarder#forward(java.lang.String)";
        String target = "example.Target#accept(java.lang.String)";
        String overloadedText = "example.Overloaded#dispatch(java.lang.String)";
        String overloadedNumber = "example.Overloaded#dispatch(java.lang.Integer)";
        FactBundle facts = new FactBundle(List.of(
                method("caller", caller, source),
                fact("caller-parameter", FactKind.SOURCE_PARAMETER, caller + "::parameter[0]:value", Map.of(
                        "owner", AtlasValue.text(caller), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("value"), "javaType", AtlasValue.text("java.lang.String"),
                        "implicit", AtlasValue.bool(false)), source),
                method("target", target, source),
                fact("target-parameter", FactKind.SOURCE_PARAMETER, target + "::parameter[0]:input", Map.of(
                        "owner", AtlasValue.text(target), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("input"), "javaType", AtlasValue.text("java.lang.String"),
                        "implicit", AtlasValue.bool(false)), source),
                method("overloaded-text", overloadedText, source),
                method("overloaded-number", overloadedNumber, source),
                fact("unique-argument-flow", FactKind.METHOD_ARGUMENT_FLOW, caller + " -> target.accept", Map.of(
                        "caller", AtlasValue.text(caller), "argumentExpression", AtlasValue.text("value"),
                        "calleeMethodName", AtlasValue.text("accept"), "argumentIndex", AtlasValue.decimal(BigDecimal.ZERO)), source),
                fact("ambiguous-argument-flow", FactKind.METHOD_ARGUMENT_FLOW, caller + " -> overloaded.dispatch", Map.of(
                        "caller", AtlasValue.text(caller), "argumentExpression", AtlasValue.text("value"),
                        "calleeMethodName", AtlasValue.text("dispatch"), "argumentIndex", AtlasValue.decimal(BigDecimal.ZERO)), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        assertEquals(1, resolution.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.ARGUMENT_BINDS_TO).count());
        assertTrue(resolution.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("AMBIGUOUS_ARGUMENT_BINDING")));
    }

    @Test
    void omitsSelfReferentialGraphEdgesButRetainsAnActionableDiagnostic() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-source");
        String recursive = "example.RetryPolicy#retry()";
        FactBundle facts = new FactBundle(List.of(
                method("recursive-method", recursive, source),
                fact("recursive-call", FactKind.METHOD_CALL, recursive + " -> retry", Map.of(
                        "caller", AtlasValue.text(recursive), "calleeOwnerHint", AtlasValue.text("example.RetryPolicy"),
                        "calleeMethodName", AtlasValue.text("retry"), "calleeExpression", AtlasValue.text("this.retry")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        assertEquals(0, resolution.designAtlas().relations().stream().filter(relation -> relation.kind() == RelationKind.CALLS).count());
        assertTrue(resolution.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("SELF_REFERENTIAL_RELATION_OMITTED")));
    }

    @Test
    void connectsDeclaredInputOutputAndOperationContractToInferredValueFlows() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-source");
        String method = "example.CustomerService#register(java.lang.String)";
        String parameter = method + "::parameter[0]:rawCustomerId";
        FactBundle facts = new FactBundle(List.of(
                fact("method", FactKind.SOURCE_METHOD, method, Map.of(
                        "owner", AtlasValue.text("example.CustomerService"),
                        "memberDescriptor", AtlasValue.text("register(java.lang.String)"),
                        "returnType", AtlasValue.text("example.CustomerId"), "operationKind", AtlasValue.text("METHOD")), source),
                fact("parameter", FactKind.SOURCE_PARAMETER, parameter, Map.of(
                        "owner", AtlasValue.text(method), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("rawCustomerId"), "javaType", AtlasValue.text("java.lang.String"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("return", FactKind.SOURCE_RETURN, method + "::return", Map.of(
                        "owner", AtlasValue.text(method), "javaType", AtlasValue.text("example.CustomerId"),
                        "constructorResult", AtlasValue.bool(false)), source)), List.of());
        Evidence declared = evidence(EvidenceOrigin.ANNOTATION_MANIFEST, EvidenceClaim.DECLARED, "annotation-processor");
        IntentTarget methodTarget = new IntentTarget("METHOD", "example.CustomerService", "register(java.lang.String)");
        IntentBundle intents = new IntentBundle(List.of(
                declaration("input", AssetKind.SEMANTIC_VALUE,
                        new IntentTarget("PARAMETER", "example.CustomerService", "register(java.lang.String)::parameter[0]:rawCustomerId"),
                        Map.of("id", AtlasValue.text("input:raw-customer-id"), "name", AtlasValue.text("原始客户标识")), declared),
                declaration("output", AssetKind.SEMANTIC_VALUE, methodTarget,
                        Map.of("id", AtlasValue.text("output:customer-id"), "name", AtlasValue.text("客户标识")), declared),
                declaration("operation", AssetKind.OPERATION_CONTRACT, methodTarget,
                        Map.of("id", AtlasValue.text("operation:register"), "name", AtlasValue.text("登记客户"),
                                "purpose", AtlasValue.text("登记一个合法客户")), declared),
                declaration("effect", AssetKind.OPERATION_CONTRACT, methodTarget,
                        Map.of("kind", AtlasValue.text("EXTERNAL_CALL"), "target", AtlasValue.text("CRM"),
                                "description", AtlasValue.text("同步客户")), declared)));

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, intents, ContentBundle.empty());

        assertTrue(resolution.designAtlas().nodes().stream().map(ArchitectureAsset::id)
                .anyMatch(id -> id.equals(AtlasId.of("input:raw-customer-id"))));
        assertTrue(resolution.designAtlas().relations().stream().anyMatch(relation -> relation.kind() == RelationKind.IMPLEMENTS
                && relation.targetId().equals(AtlasId.of("operation:register"))));
        assertTrue(resolution.designAtlas().relations().stream().anyMatch(relation -> relation.kind() == RelationKind.DERIVES_FROM
                && relation.targetId().equals(AtlasId.of("input:raw-customer-id"))));
        assertTrue(resolution.designAtlas().nodes().stream().filter(OperationContract.class::isInstance).map(OperationContract.class::cast)
                .filter(operation -> operation.id().value().startsWith("derived:operation:"))
                .flatMap(operation -> operation.outcomes().stream())
                .anyMatch(outcome -> outcome.kind() == OperationOutcomeKind.EXTERNAL_CALL && outcome.name().equals("CRM")));
    }

    @Test
    void resolvesGatewayVariableToItsDeclaredInjectionInterfaceBeforeScenarioDispatch() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "source-and-spring");
        String caller = "example.PaymentService#submit(example.Command)";
        String gatewayMethod = "example.PaymentGateway#charge(example.Command)";
        String remoteMethod = "example.RemoteGateway#charge(example.Command)";
        FactBundle facts = new FactBundle(List.of(
                method("payment-service-method", caller, source),
                method("gateway-method", gatewayMethod, source),
                method("remote-method", remoteMethod, source),
                fact("gateway-injection", FactKind.BEAN_INJECTION, "example.PaymentService#gateway", Map.of(
                        "ownerType", AtlasValue.text("example.PaymentService"),
                        "ownerBeanName", AtlasValue.text("paymentService"),
                        "memberName", AtlasValue.text("gateway"),
                        "requestedType", AtlasValue.text("example.PaymentGateway"),
                        "injectionKind", AtlasValue.text("FIELD"),
                        "cardinality", AtlasValue.text("SINGLE"),
                        "required", AtlasValue.bool(true)), source),
                fact("gateway-call", FactKind.METHOD_CALL, caller + " -> gateway.charge", Map.of(
                        "caller", AtlasValue.text(caller),
                        "calleeOwnerHint", AtlasValue.text("gateway"),
                        "calleeMethodName", AtlasValue.text("charge"),
                        "calleeExpression", AtlasValue.text("gateway.charge")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());
        var call = resolution.designAtlas().relations().stream().filter(relation -> relation.kind() == RelationKind.CALLS).findFirst().orElseThrow();
        CodeElement target = resolution.designAtlas().nodes().stream().filter(CodeElement.class::isInstance).map(CodeElement.class::cast)
                .filter(code -> code.id().equals(call.targetId())).findFirst().orElseThrow();

        assertEquals(gatewayMethod, target.qualifiedName());
        assertEquals("example.PaymentGateway", ((AtlasValue.Text) call.attributes().get("receiverDeclaredType")).value());
        assertEquals("gateway.charge", ((AtlasValue.Text) call.attributes().get("calleeExpression")).value());
    }

    @Test
    void resolvesAHelperGatewayDispatchFromTheConcreteCommandArgumentBoundToItsParameter() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-and-spring-source");
        String endpoint = "example.MobileAuthenticationController#authenticate(example.AuthenticateCommand)";
        String helper = "example.MobileAuthenticationController#sendAndRequireResult(example.Command,java.lang.Class)";
        String unrelatedHelper = "example.RegistrationController#sendAndRequireResult(example.Command,java.lang.Class)";
        String commandHandler = "example.MobileAuthenticationHandler#handle(example.AuthenticateCommand)";
        FactBundle facts = new FactBundle(List.of(
                method("endpoint", endpoint, source),
                fact("endpoint-command", FactKind.SOURCE_PARAMETER, endpoint + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(endpoint), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.AuthenticateCommand"),
                        "implicit", AtlasValue.bool(false)), source),
                method("helper", helper, source),
                fact("helper-command", FactKind.SOURCE_PARAMETER, helper + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(helper), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.Command"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("helper-result-type", FactKind.SOURCE_PARAMETER, helper + "::parameter[1]:resultType", Map.of(
                        "owner", AtlasValue.text(helper), "parameterIndex", AtlasValue.decimal(BigDecimal.ONE),
                        "parameterName", AtlasValue.text("resultType"), "javaType", AtlasValue.text("java.lang.Class"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("argument-binding", FactKind.METHOD_ARGUMENT_FLOW, endpoint + " -> sendAndRequireResult", Map.of(
                        "caller", AtlasValue.text(endpoint), "calleeMethodName", AtlasValue.text("sendAndRequireResult"),
                        "calleeOwnerHint", AtlasValue.text("example.MobileAuthenticationController"),
                        "argumentExpression", AtlasValue.text("command"), "argumentIndex", AtlasValue.decimal(BigDecimal.ZERO)), source),
                method("unrelated-helper", unrelatedHelper, source),
                fact("unrelated-helper-command", FactKind.SOURCE_PARAMETER, unrelatedHelper + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(unrelatedHelper), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.Command"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("unrelated-helper-result-type", FactKind.SOURCE_PARAMETER, unrelatedHelper + "::parameter[1]:resultType", Map.of(
                        "owner", AtlasValue.text(unrelatedHelper), "parameterIndex", AtlasValue.decimal(BigDecimal.ONE),
                        "parameterName", AtlasValue.text("resultType"), "javaType", AtlasValue.text("java.lang.Class"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("dispatch", FactKind.COMMAND_DISPATCH, helper + " -> <unresolved:command>", Map.of(
                        "caller", AtlasValue.text(helper), "commandType", AtlasValue.text("<unresolved:command>"),
                        "commandExpression", AtlasValue.text("command"), "dispatcher", AtlasValue.text("gateway"),
                        "dispatchMethod", AtlasValue.text("sendCommandAndWaitForAResult")), source),
                method("handler", commandHandler, source),
                fact("command-handler", FactKind.COMMAND_HANDLER, commandHandler, Map.of(
                        "handler", AtlasValue.text(commandHandler), "commandType", AtlasValue.text("example.AuthenticateCommand")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        AtlasId commandCode = AtlasId.of("code:" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("example.AuthenticateCommand".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(resolution.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.DISPATCHES)
                .anyMatch(relation -> relation.targetId().equals(commandCode)
                        && relation.sourceId().equals(AtlasId.of("code:" + java.util.Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(endpoint.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                        && "INTERPROCEDURAL_ARGUMENT".equals(((AtlasValue.Text) relation.attributes().get("commandResolution")).value())));
        assertTrue(resolution.designAtlas().relations().stream()
                .anyMatch(relation -> relation.kind() == RelationKind.HANDLES && relation.targetId().equals(commandCode)));
        assertTrue(resolution.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("UNRESOLVED_COMMAND_DISPATCH_ARGUMENT")));
    }

    @Test
    void resolvesAHelperGatewayDispatchFromAnExactlyTypedConstructedArgument() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-and-spring-source");
        String endpoint = "example.OrderController#create()";
        String helper = "example.OrderController#send(example.Command)";
        String commandHandler = "example.CreateOrderHandler#handle(example.CreateOrderCommand)";
        FactBundle facts = new FactBundle(List.of(
                method("endpoint", endpoint, source),
                method("helper", helper, source),
                fact("helper-command", FactKind.SOURCE_PARAMETER, helper + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(helper), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.Command"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("constructed-argument", FactKind.METHOD_ARGUMENT_FLOW, endpoint + " -> send", Map.of(
                        "caller", AtlasValue.text(endpoint), "calleeOwnerHint", AtlasValue.text("example.OrderController"),
                        "calleeMethodName", AtlasValue.text("send"), "argumentExpression", AtlasValue.text("new CreateOrderCommand()"),
                        "argumentStaticType", AtlasValue.text("example.CreateOrderCommand"),
                        "argumentIndex", AtlasValue.decimal(BigDecimal.ZERO)), source),
                fact("dispatch", FactKind.COMMAND_DISPATCH, helper + " -> <unresolved:command>", Map.of(
                        "caller", AtlasValue.text(helper), "commandType", AtlasValue.text("<unresolved:command>"),
                        "commandExpression", AtlasValue.text("command"), "dispatcher", AtlasValue.text("gateway"),
                        "dispatchMethod", AtlasValue.text("sendCommand")), source),
                method("handler", commandHandler, source),
                fact("command-handler", FactKind.COMMAND_HANDLER, commandHandler, Map.of(
                        "handler", AtlasValue.text(commandHandler), "commandType", AtlasValue.text("example.CreateOrderCommand")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        AtlasId commandCode = AtlasId.of("code:" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("example.CreateOrderCommand".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(resolution.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.DISPATCHES)
                .anyMatch(relation -> relation.targetId().equals(commandCode)
                        && "INTERPROCEDURAL_ARGUMENT".equals(((AtlasValue.Text) relation.attributes().get("commandResolution")).value())));
        assertTrue(resolution.designAtlas().relations().stream()
                .anyMatch(relation -> relation.kind() == RelationKind.HANDLES && relation.targetId().equals(commandCode)));
    }

    @Test
    void resolvesAHelperGatewayDispatchFromTheUniqueSourceReturnTypeOfAFluentArgument() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "jvm-and-spring-source");
        String endpoint = "example.OrderController#create(example.CreateOrderCommand)";
        String helper = "example.OrderController#send(example.Command)";
        String fluent = "example.CreateOrderCommand#withServerContext(java.lang.String)";
        String commandHandler = "example.CreateOrderHandler#handle(example.CreateOrderCommand)";
        FactBundle facts = new FactBundle(List.of(
                method("endpoint", endpoint, source),
                fact("endpoint-command", FactKind.SOURCE_PARAMETER, endpoint + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(endpoint), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.CreateOrderCommand"),
                        "implicit", AtlasValue.bool(false)), source),
                method("helper", helper, source),
                fact("helper-command", FactKind.SOURCE_PARAMETER, helper + "::parameter[0]:command", Map.of(
                        "owner", AtlasValue.text(helper), "parameterIndex", AtlasValue.decimal(BigDecimal.ZERO),
                        "parameterName", AtlasValue.text("command"), "javaType", AtlasValue.text("example.Command"),
                        "implicit", AtlasValue.bool(false)), source),
                fact("fluent", FactKind.SOURCE_METHOD, fluent, Map.of(
                        "owner", AtlasValue.text("example.CreateOrderCommand"),
                        "memberDescriptor", AtlasValue.text("withServerContext(java.lang.String)"),
                        "returnType", AtlasValue.text("example.CreateOrderCommand"), "operationKind", AtlasValue.text("METHOD")), source),
                fact("fluent-binding", FactKind.METHOD_ARGUMENT_FLOW, endpoint + " -> send", Map.of(
                        "caller", AtlasValue.text(endpoint), "calleeOwnerHint", AtlasValue.text("example.OrderController"),
                        "calleeMethodName", AtlasValue.text("send"),
                        "argumentExpression", AtlasValue.text("command.withServerContext(\"trusted\")"),
                        "argumentStaticType", AtlasValue.text(""),
                        "argumentReceiverExpression", AtlasValue.text("command"),
                        "argumentInvocationMethodName", AtlasValue.text("withServerContext"),
                        "argumentInvocationArgumentCount", AtlasValue.decimal(BigDecimal.ONE),
                        "argumentIndex", AtlasValue.decimal(BigDecimal.ZERO)), source),
                fact("dispatch", FactKind.COMMAND_DISPATCH, helper + " -> <unresolved:command>", Map.of(
                        "caller", AtlasValue.text(helper), "commandType", AtlasValue.text("<unresolved:command>"),
                        "commandExpression", AtlasValue.text("command"), "dispatcher", AtlasValue.text("gateway"),
                        "dispatchMethod", AtlasValue.text("sendCommand")), source),
                method("handler", commandHandler, source),
                fact("command-handler", FactKind.COMMAND_HANDLER, commandHandler, Map.of(
                        "handler", AtlasValue.text(commandHandler), "commandType", AtlasValue.text("example.CreateOrderCommand")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());

        AtlasId commandCode = AtlasId.of("code:" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("example.CreateOrderCommand".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(resolution.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.DISPATCHES)
                .anyMatch(relation -> relation.targetId().equals(commandCode)
                        && "INTERPROCEDURAL_ARGUMENT".equals(((AtlasValue.Text) relation.attributes().get("commandResolution")).value())));
    }

    @Test
    void preservesRedactedConfigurationDefaultsAsSecretMetadataInsteadOfReintroducingTheirValue() {
        Evidence source = evidence(EvidenceOrigin.SOURCE_CODE, EvidenceClaim.DERIVED, "spring-source");
        FactBundle facts = new FactBundle(List.of(fact("credential-property", FactKind.CONFIGURATION_PROPERTY,
                "payment.access-token", Map.of(
                        "propertyKey", AtlasValue.text("payment.access-token"),
                        "declaredDefault", AtlasValue.text(""),
                        "declaredDefaultRedacted", AtlasValue.bool(true),
                        "owner", AtlasValue.text("example.PaymentProperties#accessToken"),
                        "javaType", AtlasValue.text("java.lang.String")), source)), List.of());

        var resolution = new DefaultDesignAtlasResolver().resolve(facts, IntentBundle.empty(), ContentBundle.empty());
        ConfigurationProperty property = resolution.designAtlas().nodes().stream().filter(ConfigurationProperty.class::isInstance)
                .map(ConfigurationProperty.class::cast).findFirst().orElseThrow();

        assertEquals(DataSensitivity.SECRET, property.sensitivity());
        assertEquals("", property.declaredDefault());
        assertTrue(((AtlasValue.Bool) property.attributes().get("declaredDefaultRedacted")).value());
    }

    private static SourceFact method(String id, String subject, Evidence evidence) {
        return fact(id, FactKind.SOURCE_METHOD, subject, Map.of(
                "owner", AtlasValue.text(subject.substring(0, subject.indexOf('#'))),
                "memberDescriptor", AtlasValue.text(subject.substring(subject.indexOf('#') + 1)),
                "returnType", AtlasValue.text("void"), "operationKind", AtlasValue.text("METHOD")), evidence);
    }

    private static SourceFact fact(String id, FactKind kind, String subject, Map<String, AtlasValue> attributes, Evidence evidence) {
        return new SourceFact(AtlasId.of("fact:" + id), "test", kind, subject, attributes, List.of(evidence));
    }

    private static IntentDeclaration declaration(
            String id, AssetKind kind, IntentTarget target, Map<String, AtlasValue> attributes, Evidence evidence) {
        String annotation = switch (id) {
            case "input" -> "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasInput";
            case "output" -> "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasOutput";
            case "operation" -> "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasOperation";
            case "effect" -> "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEffect";
            default -> "test." + id;
        };
        return new IntentDeclaration(AtlasId.of("intent:" + id), annotation, kind, target, attributes, evidence);
    }

    private static Evidence evidence(EvidenceOrigin origin, EvidenceClaim claim, String analyzer) {
        return new Evidence(AtlasId.of("evidence:" + origin + ":" + analyzer), origin, claim, EvidenceStatus.VERIFIED,
                new SourceLocator("project://test/src/main/java/example/CustomerService.java", "example.CustomerService", SourceRange.unknown()),
                "test", EvidenceVisibility.EXPERT_ONLY, analyzer, "test evidence");
    }
}
