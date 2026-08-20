package io.github.vicliu624.explicitarchitecture.atlas.maven;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasIntentManifestReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.ConventionProfileJsonReader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * 在被分析 Maven 工程的 {@code package} 阶段生成一个可离线读取的 Atlas Package。
 *
 * <p>插件本体只携带 JVM 与 Maven 两个通用分析器。使用者若希望识别 Spring、Axon、JPA 等事实，必须在该
 * Maven plugin 的 {@code <dependencies>} 内显式添加对应的 Atlas adapter；适配器经由 {@link ServiceLoader}
 * 发现，不会被基础插件暗中引入。</p>
 */
@Mojo(name = "extract", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class AtlasExtractMojo extends AbstractMojo {

    private static final String DEFAULT_INTENT_MANIFEST = "target/classes/META-INF/explicit-architecture-atlas/atlas-intent.json";

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File projectDirectory;

    @Parameter(defaultValue = "${project.groupId}", readonly = true, required = true)
    private String projectGroupId;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true, required = true)
    private String projectArtifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true, required = true)
    private String projectVersion;

    /** 输出 Workspace 根；默认受 Maven target/ 管理，不会写入源码目录或外部业务 Workspace。 */
    @Parameter(defaultValue = "${project.build.directory}/atlas", required = true)
    private File outputDirectory;

    /** 稳定 Package 身份；未指定时由 Maven GAV 与 sourceRevision 确定性生成。 */
    @Parameter
    private String packageId;

    /** 稳定项目身份；未指定时由 Maven groupId/artifactId 确定性生成。 */
    @Parameter
    private String projectId;

    /** 用于所有证据的源版本；默认 Maven 项目版本，可由 CI 注入 commit SHA。 */
    @Parameter(defaultValue = "${project.version}", required = true)
    private String sourceRevision;

    /** 可选 Atlas Convention Profile JSON。相对路径相对于 Maven project.basedir。 */
    @Parameter
    private String conventionProfile;

    /**
     * 可选意图清单。没有显式提供时，插件读取注解处理器的默认输出；不存在默认文件时视作无意图而非失败。
     */
    @Parameter
    private String[] intentManifests;

    /** 仅分析这些源码根；相对路径相对于 Maven project.basedir。 */
    @Parameter
    private String[] sourceRoots;

    /** 永不分析的路径；相对路径相对于 Maven project.basedir。 */
    @Parameter
    private String[] excludedPaths;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Path root = requiredDirectory(projectDirectory, "project.basedir");
            String revision = requiredText(sourceRevision, "sourceRevision");
            AtlasId resolvedProjectId = AtlasId.of(valueOrDefault(projectId,
                    "project:maven:" + requiredText(projectGroupId, "project.groupId") + ":" + requiredText(projectArtifactId, "project.artifactId")));
            AtlasId resolvedPackageId = AtlasId.of(valueOrDefault(packageId,
                    "package:maven:" + projectGroupId.trim() + ":" + projectArtifactId.trim() + ":" + revision));
            Path output = resolve(root, Objects.requireNonNull(outputDirectory, "outputDirectory 不能为空").toPath());

            ConventionProfile profile = conventionProfile == null || conventionProfile.isBlank()
                    ? ConventionProfile.empty()
                    : new ConventionProfileJsonReader().read(resolve(root, Path.of(conventionProfile)));
            IntentBundle intents = readIntents(root, revision);
            AtlasPackageFileRepository repository = new AtlasPackageFileRepository(new AtlasWorkspacePaths(output));
            var atlasPackage = new DefaultAtlasBuildService(repository, installedAnalyzers(), new DefaultDesignAtlasResolver())
                    .build(new AtlasBuildRequest(
                            resolvedPackageId,
                            resolvedProjectId,
                            root,
                            revision,
                            profile,
                            intents,
                            ContentBundle.empty(),
                            resolveAll(root, sourceRoots),
                            resolveAll(root, excludedPaths)));

            getLog().info("Atlas Package 已生成：" + repository.fileOf(atlasPackage.manifest().packageId()));
            getLog().info("Atlas 事实摘要：节点=" + atlasPackage.designAtlas().nodes().size()
                    + "，关系=" + atlasPackage.designAtlas().relations().size()
                    + "，事实=" + atlasPackage.facts().facts().size()
                    + "，分析器=" + atlasPackage.manifest().coverage().size());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MojoExecutionException("Atlas extract 失败：" + exception.getMessage(), exception);
        }
    }

    private IntentBundle readIntents(Path projectRoot, String revision) {
        List<Path> manifests = new ArrayList<>();
        if (intentManifests != null && intentManifests.length > 0) {
            for (String manifest : intentManifests) {
                manifests.add(resolve(projectRoot, Path.of(requiredText(manifest, "intentManifests"))));
            }
        } else {
            Path generated = projectRoot.resolve(DEFAULT_INTENT_MANIFEST).normalize();
            if (Files.isRegularFile(generated)) {
                manifests.add(generated);
            }
        }
        return manifests.isEmpty() ? IntentBundle.empty() : new AtlasIntentManifestReader().read(manifests, revision);
    }

    private static List<SourceFactsAnalyzer> installedAnalyzers() {
        List<SourceFactsAnalyzer> analyzers = ServiceLoader.load(SourceFactsAnalyzer.class, AtlasExtractMojo.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(
                        analyzer -> analyzer.descriptor().id(),
                        analyzer -> analyzer,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(analyzer -> analyzer.descriptor().id()))
                .toList();
        if (analyzers.isEmpty()) {
            throw new IllegalStateException("未发现 Atlas SourceFactsAnalyzer；请检查 Maven plugin 依赖是否包含 atlas-source-jvm 或其他 adapter");
        }
        return analyzers;
    }

    private static List<Path> resolveAll(Path root, String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(values)
                .map(value -> resolve(root, Path.of(requiredText(value, "路径"))))
                .distinct().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private static Path requiredDirectory(File directory, String property) {
        Path path = Objects.requireNonNull(directory, property + " 不能为空").toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(property + " 不是目录: " + path);
        }
        return path;
    }

    private static Path resolve(Path root, Path configured) {
        Path path = configured.isAbsolute() ? configured : root.resolve(configured);
        return path.toAbsolutePath().normalize();
    }

    private static String valueOrDefault(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }

    private static String requiredText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " 不能为空白");
        }
        return value.trim();
    }
}
