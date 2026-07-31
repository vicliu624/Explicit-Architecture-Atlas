package io.github.vicliu624.explicitarchitecture.atlas.source.archive;

import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从 source JAR 或普通 ZIP 读取 Atlas 专家模式需要的源码。
 *
 * <p>Package 只保存 {@code project://} 定位符，绝不保存本机源归档绝对路径；Workspace 在本地提供归档列表。
 * 本适配器将两者在用户设备上安全地结合，供源码查看器显示调用链上的实现。</p>
 */
public final class SourceArchiveReader {

    /** 从某个明确归档读取一个明确条目。 */
    public Optional<String> readUtf8(Path archive, String entryName) {
        Objects.requireNonNull(archive, "archive 不能为空");
        Objects.requireNonNull(entryName, "entryName 不能为空");
        String normalizedEntry = normalizeEntryName(entryName);
        try (ZipFile zipFile = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(normalizedEntry);
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

    /**
     * 按 Atlas 的项目源码定位符，在 Workspace 给出的 source JAR 列表中查找源码。
     *
     * <p>支持 source JAR 常见的两种布局：直接包含 {@code example/Order.java}，或保留
     * {@code src/main/java/example/Order.java}。定位符不是 {@code project://} 或路径不安全时返回空结果。</p>
     */
    public Optional<SourceArchiveMatch> readForLocator(SourceLocator locator, List<Path> sourceArchives) {
        Objects.requireNonNull(locator, "locator 不能为空");
        sourceArchives = List.copyOf(Objects.requireNonNull(sourceArchives, "sourceArchives 不能为空"));
        Optional<String> relativeSource = projectRelativePath(locator.uri());
        if (relativeSource.isEmpty()) {
            return Optional.empty();
        }
        for (Path archive : sourceArchives) {
            Path normalizedArchive = Objects.requireNonNull(archive, "sourceArchive 不能为空").toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedArchive)) {
                continue;
            }
            for (String entryName : candidateEntries(relativeSource.get())) {
                Optional<String> content = readUtf8(normalizedArchive, entryName);
                if (content.isPresent()) {
                    return Optional.of(new SourceArchiveMatch(normalizedArchive, entryName, content.get()));
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> candidateEntries(String relativeSource) {
        String normalized = normalizeEntryName(relativeSource);
        if (normalized.startsWith("src/main/java/")) {
            return List.of(normalized, normalized.substring("src/main/java/".length()));
        }
        return List.of(normalized);
    }

    private static Optional<String> projectRelativePath(String uri) {
        if (uri == null || !uri.startsWith("project://")) {
            return Optional.empty();
        }
        int pathStart = uri.indexOf('/', "project://".length());
        if (pathStart < 0 || pathStart == uri.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalizeEntryName(URLDecoder.decode(uri.substring(pathStart + 1), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException invalidEncoding) {
            return Optional.empty();
        }
    }

    private static String normalizeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/').strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty() || normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../")) {
            throw new IllegalArgumentException("源码归档条目路径不安全: " + entryName);
        }
        return normalized;
    }

    /** 本地归档中命中的源码，不进入 Package，也不暴露为持久化的架构事实。 */
    public record SourceArchiveMatch(Path archive, String entryName, String content) {
        public SourceArchiveMatch {
            archive = Objects.requireNonNull(archive, "archive 不能为空").toAbsolutePath().normalize();
            entryName = normalizeEntryName(entryName);
            content = Objects.requireNonNull(content, "content 不能为空");
        }
    }
}
