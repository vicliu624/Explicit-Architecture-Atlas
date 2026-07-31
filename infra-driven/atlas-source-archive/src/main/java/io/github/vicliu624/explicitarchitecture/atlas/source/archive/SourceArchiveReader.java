package io.github.vicliu624.explicitarchitecture.atlas.source.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从 source JAR 或普通 ZIP 读取指定源码条目。
 *
 * <p>专家模式通过该适配器显示调用链上的源码；业务、运维视图只消费同一节点的语义摘要。</p>
 */
public final class SourceArchiveReader {

    public Optional<String> readUtf8(Path archive, String entryName) {
        try (ZipFile zipFile = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                return Optional.empty();
            }
            try (var input = zipFile.getInputStream(entry)) {
                return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("无法读取源码归档: " + archive, exception);
        }
    }
}
