package io.github.vicliu624.explicitarchitecture.atlas.maven;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasExtractMojoTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAnOfflinePackageWithGenericJvmAndMavenFacts() throws Exception {
        Path project = temporaryDirectory.resolve("sample-project");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>checkout</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Files.writeString(project.resolve("src/main/java/example/Checkout.java"), """
                package example;
                final class Checkout {
                    String submit(String orderId) { return orderId.trim(); }
                }
                """);
        Path output = project.resolve("target/atlas");

        AtlasExtractMojo mojo = new AtlasExtractMojo();
        set(mojo, "projectDirectory", project.toFile());
        set(mojo, "projectGroupId", "example");
        set(mojo, "projectArtifactId", "checkout");
        set(mojo, "projectVersion", "1.0.0");
        set(mojo, "sourceRevision", "commit-42");
        set(mojo, "outputDirectory", output.toFile());

        mojo.execute();

        var atlasPackage = new AtlasPackageFileRepository(new AtlasWorkspacePaths(output))
                .findById(AtlasId.of("package:maven:example:checkout:commit-42"))
                .orElseThrow();
        assertEquals(AtlasId.of("project:maven:example:checkout"), atlasPackage.manifest().projectId());
        assertTrue(atlasPackage.manifest().coverage().stream()
                .anyMatch(coverage -> coverage.analyzerId().equals("analyzer:jvm-source")));
        assertTrue(atlasPackage.manifest().coverage().stream()
                .anyMatch(coverage -> coverage.analyzerId().equals("analyzer:maven-source")));
        assertTrue(atlasPackage.designAtlas().nodes().stream()
                .filter(CodeElement.class::isInstance).map(CodeElement.class::cast)
                .anyMatch(node -> node.qualifiedName().equals("example.Checkout")));
    }

    private static void set(AtlasExtractMojo mojo, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = AtlasExtractMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}
