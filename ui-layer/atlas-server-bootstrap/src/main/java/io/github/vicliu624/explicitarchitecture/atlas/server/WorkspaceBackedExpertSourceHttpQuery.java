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

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 仅从 Workspace 显式声明的 source JAR 读取专家源码。
 *
 * <p>它先确认资产属于指定 Package，再解析 CodeElement 或唯一 {@code REPRESENTS} 映射；因此 HTTP 客户端无法利用
 * 此服务读取本机任意文件，也永远不会获得 source JAR 的绝对路径。</p>
 */
public final class WorkspaceBackedExpertSourceHttpQuery implements AtlasExpertSourceHttpQuery {

    private final AtlasPackageQueryUseCase packageQuery;
    private final SourceArchiveReader sourceArchiveReader;
    private final List<Path> sourceArchives;

    public WorkspaceBackedExpertSourceHttpQuery(
            AtlasPackageQueryUseCase packageQuery,
            SourceArchiveReader sourceArchiveReader,
            List<Path> sourceArchives) {
        this.packageQuery = Objects.requireNonNull(packageQuery, "packageQuery 不能为空");
        this.sourceArchiveReader = Objects.requireNonNull(sourceArchiveReader, "sourceArchiveReader 不能为空");
        this.sourceArchives = List.copyOf(Objects.requireNonNull(sourceArchives, "sourceArchives 不能为空"));
    }

    @Override
    public Optional<ExpertAtlasView.SourceFile> findSource(AtlasId packageId, AtlasId assetId) {
        Objects.requireNonNull(packageId, "packageId 不能为空");
        Objects.requireNonNull(assetId, "assetId 不能为空");
        return packageQuery.findById(packageId).flatMap(atlasPackage -> codeFor(atlasPackage, assetId)
                .flatMap(code -> sourceArchiveReader.readForLocator(code.locator(), sourceArchives)
                        .map(match -> new ExpertAtlasView.SourceFile(
                                assetId.value(), code.qualifiedName(), location(code), match.entryName(), match.content()))));
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
}
