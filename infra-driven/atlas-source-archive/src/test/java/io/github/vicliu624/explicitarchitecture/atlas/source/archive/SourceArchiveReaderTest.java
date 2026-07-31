package io.github.vicliu624.explicitarchitecture.atlas.source.archive;

import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceArchiveReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesProjectLocatorAgainstStandardSourceJarLayout() throws IOException {
        Path sourceJar = temporaryDirectory.resolve("fixture-sources.jar");
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(sourceJar))) {
            zip.putNextEntry(new ZipEntry("example/fixture/OrderEndpoint.java"));
            zip.write("package example.fixture;\nclass OrderEndpoint {}\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        SourceArchiveReader reader = new SourceArchiveReader();
        SourceLocator locator = new SourceLocator(
                "project://project:fixture/src/main/java/example/fixture/OrderEndpoint.java",
                "example.fixture.OrderEndpoint#create()",
                SourceRange.unknown());

        SourceArchiveReader.SourceArchiveMatch match = reader.readForLocator(locator, List.of(sourceJar)).orElseThrow();
        assertEquals("example/fixture/OrderEndpoint.java", match.entryName());
        assertTrue(match.content().contains("OrderEndpoint"));
        assertFalse(reader.readForLocator(new SourceLocator("generated://intent", "intent", SourceRange.unknown()), List.of(sourceJar)).isPresent());
    }

    @Test
    void rejectsTraversalBeforeReadingArchive() {
        SourceArchiveReader reader = new SourceArchiveReader();
        assertThrows(IllegalArgumentException.class, () -> reader.readUtf8(temporaryDirectory.resolve("none.jar"), "../secret.java"));
    }
}
