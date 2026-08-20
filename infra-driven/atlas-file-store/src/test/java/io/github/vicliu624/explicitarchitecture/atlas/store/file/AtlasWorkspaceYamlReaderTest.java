package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasWorkspaceYamlReaderTest {

    @TempDir
    Path workspace;

    @Test
    void readsSourceScopeAndSafeNamedConfigurationScenario() throws Exception {
        Path project = workspace.resolve("project");
        Files.createDirectories(project.resolve("service/src/main/java"));
        Path configuration = workspace.resolve("atlas-workspace.yaml");
        Files.writeString(configuration, """
                schemaVersion: "1.0"
                workspaceId: "workspace:demo"
                project:
                  id: "project:demo"
                  displayName: "Demo"
                sources:
                  projectRoot: "project"
                  sourceRoots: ["project/service/src/main/java"]
                  excludedPaths: ["project/.tmp"]
                  sourceArchives: []
                  intentManifests: []
                scenarios:
                  - id: "scenario:prod"
                    name: "production"
                    activeProfiles: ["prod"]
                    properties:
                      payment.enabled:
                        provenance: "application-prod.yml"
                        value: "true"
                        redacted: false
                      payment.token:
                        provenance: "vault"
                        redacted: true
                    classpathCapabilities: ["com.example.PaymentClient"]
                    applicationContext:
                      id: "context:demo-api"
                      entryPointTypes: ["example.DemoApplication"]
                      sourcePathPrefixes: ["service", "adapter/api"]
                      componentScanPackages: ["example"]
                      beanDefinitionOverridePolicy: "ALLOW"
                      explicitBeanOverrides:
                        paymentGateway: "example.RemotePaymentGateway"
                """);

        var loaded = new AtlasWorkspaceYamlReader().read(configuration);

        assertEquals(project.resolve("service/src/main/java").toAbsolutePath().normalize(), loaded.sources().sourceRoots().getFirst());
        assertEquals("true", loaded.scenarios().getFirst().properties().get("payment.enabled").value());
        assertTrue(loaded.scenarios().getFirst().properties().get("payment.token").redacted());
        assertEquals("", loaded.scenarios().getFirst().properties().get("payment.token").value());
        assertEquals("context:demo-api", loaded.scenarios().getFirst().applicationContext().contextId());
        assertEquals(List.of("adapter/api/", "service/"), loaded.scenarios().getFirst().applicationContext().sourcePathPrefixes());
        assertEquals("example.RemotePaymentGateway", loaded.scenarios().getFirst().applicationContext()
                .explicitBeanOverrides().get("paymentGateway"));
    }
}
