package io.github.vicliu624.explicitarchitecture.atlas.source.jvm;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmSourceFactsAnalyzerTest {

    @TempDir
    Path project;

    @Test
    void extractsAStaticPatternAsAValidationFactInsteadOfIgnoringFieldInitializers() throws IOException {
        Path source = project.resolve("src/main/java/example/CustomerId.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                import java.util.regex.Pattern;
                final class CustomerId {
                    private static final Pattern FORMAT = Pattern.compile("^\\\\d{19}1$");
                }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:static-pattern"), project, "revision:test"));

        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.SOURCE_FIELD));
        var pattern = contribution.facts().stream()
                .filter(fact -> fact.kind() == FactKind.VALIDATION_RULE)
                .filter(fact -> "PATTERN".equals(((AtlasValue.Text) fact.attributes().get("ruleType")).value()))
                .findFirst()
                .orElseThrow();
        assertEquals("example.CustomerId", ((AtlasValue.Text) pattern.attributes().get("owner")).value());
        assertEquals("\"^\\\\d{19}1$\"", ((AtlasValue.Text) pattern.attributes().get("expression")).value());
    }

    @Test
    void keepsEnumConstantsAndExposesParametersReturnsAndArgumentFlows() throws IOException {
        Path source = project.resolve("src/main/java/example/OperationSample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                enum Lifecycle { CREATED }
                final class OperationSample {
                    static String echo(String input) {
                        return input;
                    }
                    static String delegate(String input) {
                        return echo(input);
                    }
                }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:operation-contract"), project, "revision:test"));

        assertFalse(contribution.coverage().status().name().equals("FAILED"));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.SOURCE_PARAMETER
                && fact.subject().contains("echo(java.lang.String)::parameter[0]:input")));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.SOURCE_RETURN
                && fact.subject().contains("echo(java.lang.String)::return")));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.METHOD_RETURN_FLOW
                && "input".equals(((AtlasValue.Text) fact.attributes().get("expression")).value())));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.METHOD_ARGUMENT_FLOW
                && "input".equals(((AtlasValue.Text) fact.attributes().get("argumentExpression")).value())
                && "example.OperationSample".equals(((AtlasValue.Text) fact.attributes().get("calleeOwnerHint")).value())));
    }

    @Test
    void recordsTheExactTypeOfAConstructedCallArgument() throws IOException {
        Path source = project.resolve("src/main/java/example/ConstructedArgument.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                class Command { }
                class CreateCommand extends Command { }
                final class ConstructedArgument {
                    static void accept(Command command) { }
                    static void forward() { accept(new CreateCommand()); }
                }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:constructed-argument"), project, "revision:test"));

        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.METHOD_ARGUMENT_FLOW
                && "new CreateCommand()".equals(((AtlasValue.Text) fact.attributes().get("argumentExpression")).value())
                && "example.CreateCommand".equals(((AtlasValue.Text) fact.attributes().get("argumentStaticType")).value())));
    }

    @Test
    void recordsAnIfBranchThatRejectsInputAsAnEnforcedGuard() throws IOException {
        Path source = project.resolve("src/main/java/example/GuardedValue.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                final class GuardedValue {
                    static String require(String value) {
                        if (value == null || value.isBlank()) {
                            throw new IllegalArgumentException("value is required");
                        }
                        return value;
                    }
                }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:if-throw-guard"), project, "revision:test"));

        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.VALIDATION_RULE
                && "IF_THROW_GUARD".equals(((AtlasValue.Text) fact.attributes().get("ruleType")).value())
                && ((AtlasValue.Text) fact.attributes().get("expression")).value().contains("value == null || value.isBlank()")));
    }

    @Test
    void restrictsAnalysisToConfiguredRootsAndNeverTreatsTemporaryOrTestTreesAsProduction() throws IOException {
        Path included = project.resolve("service/src/main/java/example/Included.java");
        Path temporary = project.resolve(".tmp/old/src/main/java/example/Temporary.java");
        Path test = project.resolve("service/src/test/java/example/TestOnly.java");
        Path extra = project.resolve("generated/java/example/Generated.java");
        writeType(included, "Included");
        writeType(temporary, "Temporary");
        writeType(test, "TestOnly");
        writeType(extra, "Generated");

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(new AnalysisRequest(
                AtlasId.of("project:source-roots"), project, "revision:test", io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile.empty(),
                List.of(project.resolve("service/src/main/java"), project.resolve("generated/java")),
                List.of(project.resolve("generated"))));

        List<String> types = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.SOURCE_TYPE)
                .map(fact -> fact.subject()).toList();
        assertTrue(types.contains("example.Included"));
        assertFalse(types.contains("example.Generated"));
        assertFalse(types.contains("example.Temporary"));
        assertFalse(types.contains("example.TestOnly"));
    }

    @Test
    void recordsDeclaredSupertypesForFrameworkIndependentAssignableTypeResolution() throws IOException {
        Path source = project.resolve("src/main/java/example/PaymentGateway.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                interface PaymentGateway { }
                class RemotePaymentGateway implements PaymentGateway { }
                class RetryingRemoteGateway extends RemotePaymentGateway { }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:type-hierarchy"), project, "revision:test"));

        var remote = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.SOURCE_TYPE)
                .filter(fact -> fact.subject().equals("example.RemotePaymentGateway")).findFirst().orElseThrow();
        AtlasValue.Values interfaces = (AtlasValue.Values) remote.attributes().get("implementsTypes");
        assertEquals(List.of("example.PaymentGateway"), interfaces.values().stream()
                .map(AtlasValue.Text.class::cast).map(AtlasValue.Text::value).toList());
        var retrying = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.SOURCE_TYPE)
                .filter(fact -> fact.subject().equals("example.RetryingRemoteGateway")).findFirst().orElseThrow();
        assertEquals("example.RemotePaymentGateway", ((AtlasValue.Text) retrying.attributes().get("extendsType")).value());
    }

    @Test
    void normalizesWildcardImportedParameterAndReturnTypesToTheSameMethodIdentityUsedByFrameworkAdapters() throws IOException {
        Path command = project.resolve("src/main/java/example/command/Submit.java");
        Path result = project.resolve("src/main/java/example/command/Submission.java");
        Path handler = project.resolve("src/main/java/example/handler/SubmitHandler.java");
        Files.createDirectories(command.getParent());
        Files.createDirectories(handler.getParent());
        Files.writeString(command, "package example.command; public class Submit {}\n");
        Files.writeString(result, "package example.command; public class Submission {}\n");
        Files.writeString(handler, """
                package example.handler;
                import example.command.*;
                final class SubmitHandler {
                    Submission handle(Submit command) { return new Submission(); }
                }
                """);

        AnalyzerContribution contribution = new JvmSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:wildcard-method"), project, "revision:test"));

        String method = "example.handler.SubmitHandler#handle(example.command.Submit)";
        var sourceMethod = contribution.facts().stream()
                .filter(fact -> fact.kind() == FactKind.SOURCE_METHOD && fact.subject().equals(method))
                .findFirst().orElseThrow();
        assertEquals("example.command.Submission", ((AtlasValue.Text) sourceMethod.attributes().get("returnType")).value());
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.SOURCE_PARAMETER
                && fact.subject().equals(method + "::parameter[0]:command")));
    }

    private static void writeType(Path source, String type) throws IOException {
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example; final class " + type + " {}\n");
    }
}
