package io.github.vicliu624.explicitarchitecture.atlas.source.axon;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxonSourceFactsAnalyzerTest {

    @TempDir
    Path project;

    @Test
    void resolvesACommandHandlerParameterImportedThroughAnUnambiguousWildcardPackage() throws IOException {
        write("src/main/java/example/command/SelectProductCommand.java", """
                package example.command;
                final class SelectProductCommand { }
                """);
        write("src/main/java/example/handler/ProductCommandHandler.java", """
                package example.handler;
                import example.command.*;
                import org.axonframework.commandhandling.CommandHandler;
                final class ProductCommandHandler {
                    @CommandHandler void handle(SelectProductCommand command) { }
                }
                """);

        var contribution = new AxonSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:wildcard-command-handler"), project, "revision:test"));

        var fact = contribution.facts().stream().filter(candidate -> candidate.kind() == FactKind.COMMAND_HANDLER)
                .findFirst().orElseThrow();
        assertEquals("example.command.SelectProductCommand",
                ((AtlasValue.Text) fact.attributes().get("commandType")).value());
    }

    private void write(String relativePath, String content) throws IOException {
        Path source = project.resolve(relativePath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }
}
