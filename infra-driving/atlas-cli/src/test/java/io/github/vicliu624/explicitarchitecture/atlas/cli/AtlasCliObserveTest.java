package io.github.vicliu624.explicitarchitecture.atlas.cli;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 CLI 可以在不连接 Axon 或数据库的情况下完成静态提取及 Overlay 叠加。 */
class AtlasCliObserveTest {

    @TempDir
    Path workspace;

    @Test
    void extractsThenOverlaysASanitizedOfflineAxonObservation() throws IOException {
        Path project = workspace.resolve("sample-project");
        Path source = project.resolve("src/main/java/example/OrderProjection.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>sample-project</artifactId><version>1.0</version>
                </project>
                """);
        Files.writeString(source, """
                package example;

                @io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProjection
                final class OrderProjection {
                    @org.axonframework.eventhandling.EventHandler
                    void on(OrderCreated event) { }
                }

                final class OrderCreated { }
                """);
        Path observationFile = workspace.resolve("runtime/order-observations.json");
        Files.createDirectories(observationFile.getParent());
        Files.writeString(observationFile, """
                {
                  "schemaVersion": "1.0",
                  "capturedAt": "2026-07-31T08:00:00Z",
                  "observations": [{
                    "id": "obs:order:success",
                    "eventType": "example.OrderCreated",
                    "handlerType": "example.OrderProjection",
                    "observedAt": "2026-07-31T07:59:59Z",
                    "status": "SUCCEEDED",
                    "attributes": {"processingGroup": "orders", "payload": "must-not-survive"}
                  }]
                }
                """);
        Path workspaceFile = workspace.resolve("atlas-workspace.yaml");
        Files.writeString(workspaceFile, """
                schemaVersion: "1.0"
                workspaceId: "workspace:cli-test"
                project:
                  id: "project:cli-test"
                  displayName: "CLI Test"
                sources:
                  projectRoot: "%s"
                  sourceArchives: []
                  intentManifests: []
                runtime:
                  enabled: true
                  observationFiles:
                    - "%s"
                """.formatted(yamlPath(project), yamlPath(observationFile)));

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBuffer, true, StandardCharsets.UTF_8);
        List<String> common = List.of("--workspace", workspaceFile.toString(), "--package-id", "package:cli:runtime");

        assertEquals(0, AtlasCli.run(join("extract", common, "--source-revision", "cli-test"), output, error));
        assertEquals(0, AtlasCli.run(join("observe", common), output, error));
        assertTrue(errorBuffer.toString(StandardCharsets.UTF_8).isBlank());
        assertTrue(outputBuffer.toString(StandardCharsets.UTF_8).contains("运行状态：SUCCEEDED = 1"));

        var restored = new AtlasPackageFileRepository(new AtlasWorkspacePaths(workspace))
                .findById(AtlasId.of("package:cli:runtime"))
                .orElseThrow();
        var observation = restored.runtimeOverlay().orElseThrow().observations().stream()
                .filter(item -> item.observationType().equals("PROJECTION_SUCCEEDED"))
                .findFirst()
                .orElseThrow();
        assertTrue(observation.attributes().containsKey("discardedAttributeCount"));
        assertTrue(!observation.attributes().containsKey("payload"));
    }

    private static List<String> join(String command, List<String> options, String... suffix) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        values.add(command);
        values.addAll(options);
        values.addAll(List.of(suffix));
        return List.copyOf(values);
    }

    private static String yamlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
