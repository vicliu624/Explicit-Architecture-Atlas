package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.ExpertAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasExpertSourceHttpQuery;
import io.github.vicliu624.explicitarchitecture.atlas.source.archive.SourceArchiveReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 从 Workspace 明确授权的项目根目录或 source JAR 读取专家源码。
 *
 * <p>它先确认资产属于指定 Package，再解析 CodeElement 或唯一 {@code REPRESENTS} 映射；因此 HTTP 客户端无法利用
 * 此服务读取本机任意文件，也永远不会获得项目根目录或 source JAR 的绝对路径。项目源码优先于归档源码，
 * 因此用户可查看当前工程的实现，而不必把工程打成 source JAR。</p>
 */
public final class WorkspaceBackedExpertSourceHttpQuery implements AtlasExpertSourceHttpQuery {

    private final AtlasPackageQueryUseCase packageQuery;
    private final Optional<Path> projectRoot;
    private final SourceArchiveReader sourceArchiveReader;
    private final List<Path> sourceArchives;

    public WorkspaceBackedExpertSourceHttpQuery(
            AtlasPackageQueryUseCase packageQuery,
            SourceArchiveReader sourceArchiveReader,
            List<Path> sourceArchives) {
        this(packageQuery, Optional.empty(), sourceArchiveReader, sourceArchives);
    }

    public WorkspaceBackedExpertSourceHttpQuery(
            AtlasPackageQueryUseCase packageQuery,
            Path projectRoot,
            SourceArchiveReader sourceArchiveReader,
            List<Path> sourceArchives) {
        this(packageQuery, Optional.of(Objects.requireNonNull(projectRoot, "projectRoot 不能为空").toAbsolutePath().normalize()),
                sourceArchiveReader, sourceArchives);
    }

    private WorkspaceBackedExpertSourceHttpQuery(
            AtlasPackageQueryUseCase packageQuery,
            Optional<Path> projectRoot,
            SourceArchiveReader sourceArchiveReader,
            List<Path> sourceArchives) {
        this.packageQuery = Objects.requireNonNull(packageQuery, "packageQuery 不能为空");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot 不能为空");
        this.sourceArchiveReader = Objects.requireNonNull(sourceArchiveReader, "sourceArchiveReader 不能为空");
        this.sourceArchives = List.copyOf(Objects.requireNonNull(sourceArchives, "sourceArchives 不能为空"));
    }

    @Override
    public Optional<ExpertAtlasView.SourceFile> findSource(AtlasId packageId, AtlasId assetId) {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Objects.requireNonNull(assetId, "assetId 不能为空");
        return packageQuery.findById(packageId).flatMap(atlasPackage -> codeFor(atlasPackage, assetId)
                .flatMap(code -> readProjectSource(code).or(() -> sourceArchiveReader.readForLocator(code.locator(), sourceArchives)
                        .map(match -> new SourceContent(match.entryName(), match.content())))
                        .map(content -> new ExpertAtlasView.SourceFile(
                                assetId.value(), code.qualifiedName(), location(code), content.entryName(), content.content()))));
    }

    /** 只接受由 Package 中 project:// 定位符指向、并且实际位于声明项目根目录内的 Java 源文件。 */
    private Optional<SourceContent> readProjectSource(CodeElement code) {
        if (projectRoot.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> relativePath = projectRelativeJavaPath(code.locator().uri());
        if (relativePath.isEmpty()) {
            return Optional.empty();
        }
        Path candidate = projectRoot.orElseThrow().resolve(relativePath.get()).normalize();
        if (!candidate.startsWith(projectRoot.orElseThrow()) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SourceContent(relativePath.get(), Files.readString(candidate, StandardCharsets.UTF_8)));
        } catch (java.io.IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> projectRelativeJavaPath(String uri) {
        if (uri == null || !uri.startsWith("project://")) {
            return Optional.empty();
        }
        int projectSeparator = uri.indexOf('/', "project://".length());
        if (projectSeparator < 0 || projectSeparator == uri.length() - 1) {
            return Optional.empty();
        }
        String relative = uri.substring(projectSeparator + 1);
        if (!relative.endsWith(".java") || relative.startsWith("/") || relative.contains("\\")
                || relative.contains(":") || relative.split("/", -1).length == 0
                || java.util.Arrays.stream(relative.split("/", -1)).anyMatch(segment -> segment.equals(".") || segment.equals("..") || segment.isBlank())) {
            return Optional.empty();
        }
        return Optional.of(relative);
    }

    private static Optional<CodeElement> codeFor(AtlasPackage atlasPackage, AtlasId assetId) {
        Optional<ArchitectureAsset> direct = atlasPackage.designAtlas().nodes().stream()
                .filter(asset -> asset.id().equals(assetId)).findFirst();
        if (direct.filter(CodeElement.class::isInstance).isPresent()) {
            return direct.map(CodeElement.class::cast);
        }
        List<CodeElement> represented = atlasPackage.designAtlas().relations().stream()
                .filter(relation -> relation.kind() == RelationKind.REPRESENTS && relation.sourceId().equals(assetId))
                .map(relation -> relation.targetId())
                .flatMap(targetId -> atlasPackage.designAtlas().nodes().stream().filter(asset -> asset.id().equals(targetId)))
                .filter(CodeElement.class::isInstance).map(CodeElement.class::cast)
                .sorted(Comparator.comparing(CodeElement::id))
                .toList();
        return represented.size() == 1 ? Optional.of(represented.getFirst()) : Optional.empty();
    }

    private static io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasLocationView location(CodeElement code) {
        return new io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasLocationView(
                code.locator().uri(), code.locator().symbol(),
                code.locator().range().startLine(), code.locator().range().startColumn(),
                code.locator().range().endLine(), code.locator().range().endColumn());
    }

    private record SourceContent(String entryName, String content) {
        private SourceContent {
            entryName = Objects.requireNonNull(entryName, "entryName 不能为空");
            content = Objects.requireNonNull(content, "content 不能为空");
        }
    }
}
