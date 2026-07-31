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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
