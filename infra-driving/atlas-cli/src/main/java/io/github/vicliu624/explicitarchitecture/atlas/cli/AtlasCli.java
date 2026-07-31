package io.github.vicliu624.explicitarchitecture.atlas.cli;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasWorkspace;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultRuntimeOverlayCompiler;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHealthStatus;
import io.github.vicliu624.explicitarchitecture.atlas.source.axon.AxonSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.jpa.JpaProjectionTargetResolver;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JvmSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.maven.MavenSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.source.spring.SpringSourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.runtime.axon.AxonRuntimeSignalMapper;
import io.github.vicliu624.explicitarchitecture.atlas.runtime.axon.JsonAxonRuntimeObservationReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasIntentManifestReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspaceYamlReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.ConventionProfileJsonReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Atlas 的可离线执行入口。第一条实现链路为 {@code extract}。 */
public final class AtlasCli {

    private AtlasCli() {
    }

    public static void main(String[] arguments) {
        // Windows 的 native stdout 可能仍是 GBK；Atlas 的 CLI 文档以 PowerShell 7 UTF-8 为基线，故显式输出 UTF-8。
        int exitCode = run(
                List.of(arguments),
                new PrintStream(System.out, true, StandardCharsets.UTF_8),
                new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(List<String> arguments, PrintStream output, PrintStream error) {
        if (arguments.isEmpty() || arguments.contains("--help") || arguments.contains("help")) {
            printHelp(output);
            return 0;
        }
        try {
            return switch (arguments.getFirst()) {
                case "extract" -> extract(arguments.subList(1, arguments.size()), output);
                case "observe" -> observe(arguments.subList(1, arguments.size()), output);
                default -> {
                    error.println("未知或暂未实现的 Atlas 命令：" + arguments.getFirst());
                    printHelp(error);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            error.println("Atlas extract 失败：" + exception.getMessage());
            return 2;
        }
    }

    private static int extract(List<String> arguments, PrintStream output) {
        Map<String, String> options = options(arguments, List.of("--workspace", "--package-id", "--source-revision"));
        Path workspaceFile = Path.of(requiredOption(options, "--workspace"));
        String packageId = requiredOption(options, "--package-id");
        String sourceRevision = options.getOrDefault("--source-revision", "working-tree");

        AtlasWorkspace workspace = new AtlasWorkspaceYamlReader().read(workspaceFile);
        ConventionProfile conventionProfile = workspace.conventionProfileFile()
                .map(path -> new ConventionProfileJsonReader().read(path))
                .orElseGet(ConventionProfile::empty);
        IntentBundle intents = new AtlasIntentManifestReader().read(workspace.sources().intentManifests(), sourceRevision);
        AtlasWorkspacePaths workspacePaths = new AtlasWorkspacePaths(workspaceFile.toAbsolutePath().normalize().getParent());
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(workspacePaths);
        AtlasPackage atlasPackage = new DefaultAtlasBuildService(
                repository,
                List.of(
                        new MavenSourceFactsAnalyzer(),
                        new JvmSourceFactsAnalyzer(),
                        new SpringSourceFactsAnalyzer(),
                        new AxonSourceFactsAnalyzer(),
                        new JpaProjectionTargetResolver()),
                new DefaultDesignAtlasResolver())
                .build(new AtlasBuildRequest(
                        AtlasId.of(packageId),
                        workspace.project().id(),
                        workspace.sources().projectRoot(),
                        sourceRevision,
                        conventionProfile,
                        intents,
                        ContentBundle.empty()));

        output.println("Atlas Package 已生成：" + repository.fileOf(atlasPackage.manifest().packageId()));
        output.println("项目：" + atlasPackage.manifest().projectId() + "；节点：" + atlasPackage.designAtlas().nodes().size()
                + "；关系：" + atlasPackage.designAtlas().relations().size() + "；事实：" + atlasPackage.facts().facts().size());
        atlasPackage.manifest().coverage().forEach(coverage -> output.println(
                "覆盖：" + coverage.analyzerId() + " = " + coverage.status() + "（" + coverage.summary() + "）"));
        return 0;
    }

    /** 将 Workspace 私有目录中的脱敏 Axon 观测叠加到已存在的静态 Package。 */
    private static int observe(List<String> arguments, PrintStream output) {
        Map<String, String> options = options(arguments, List.of("--workspace", "--package-id"));
        Path workspaceFile = Path.of(requiredOption(options, "--workspace"));
        AtlasWorkspace workspace = new AtlasWorkspaceYamlReader().read(workspaceFile);
        var runtime = workspace.runtime()
                .filter(configuration -> configuration.enabled())
                .orElseThrow(() -> new IllegalArgumentException("Workspace 未启用 runtime.enabled，拒绝导入运行态观测"));
        if (runtime.observationFiles().isEmpty()) {
            throw new IllegalArgumentException("Workspace runtime.observationFiles 不能为空；观测文件必须保留在私有 Workspace 内");
        }
        AtlasWorkspacePaths workspacePaths = new AtlasWorkspacePaths(workspaceFile.toAbsolutePath().normalize().getParent());
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(workspacePaths);
        AtlasId packageId = AtlasId.of(requiredOption(options, "--package-id"));
        AtlasPackage staticPackage = repository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("找不到需要叠加运行态的 Atlas Package: " + packageId));
        var signalBatch = new AxonRuntimeSignalMapper().map(
                new JsonAxonRuntimeObservationReader(runtime.observationFiles()).read());
        var result = new DefaultRuntimeOverlayCompiler().compile(new RuntimeOverlayCompileRequest(staticPackage, signalBatch));
        AtlasPackage updated = new AtlasPackage(
                staticPackage.manifest(),
                staticPackage.designAtlas(),
                staticPackage.facts(),
                staticPackage.intents(),
                staticPackage.content(),
                Optional.of(result.overlay()),
                mergeDiagnostics(staticPackage, result));
        repository.save(updated);

        output.println("Runtime Overlay 已叠加：" + repository.fileOf(updated.manifest().packageId()));
        output.println("采集时间：" + result.overlay().capturedAt() + "；输入信号：" + signalBatch.signals().size()
                + "；已关联观测：" + result.overlay().observations().size());
        Map<RuntimeHealthStatus, Long> healthCounts = result.health().stream()
                .collect(Collectors.groupingBy(RuntimeAssetHealth::status, java.util.TreeMap::new, Collectors.counting()));
        healthCounts.forEach((status, count) -> output.println("运行状态：" + status + " = " + count));
        result.health().stream()
                .filter(health -> health.status() != RuntimeHealthStatus.SUCCEEDED)
                .forEach(health -> output.println("运维关注：" + health.operationalSummary() + " 建议：" + health.suggestedAction()));
        result.diagnostics().forEach(diagnostic -> output.println(
                "运行态诊断：" + diagnostic.code() + "（" + diagnostic.message() + "）"));
        return 0;
    }

    private static List<io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic> mergeDiagnostics(
            AtlasPackage atlasPackage,
            io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileResult result) {
        return java.util.stream.Stream.concat(atlasPackage.diagnostics().stream(), result.diagnostics().stream())
                .collect(Collectors.toMap(
                        io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic::id,
                        diagnostic -> diagnostic,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    private static Map<String, String> options(List<String> arguments, List<String> supportedOptions) {
        Map<String, String> options = new java.util.LinkedHashMap<>();
        for (int index = 0; index < arguments.size(); index += 2) {
            String key = arguments.get(index);
            if (!key.startsWith("--") || index + 1 >= arguments.size()) {
                throw new IllegalArgumentException("参数必须使用 --名称 值 的形式");
            }
            if (options.putIfAbsent(key, arguments.get(index + 1)) != null) {
                throw new IllegalArgumentException("参数重复: " + key);
            }
        }
        for (String key : options.keySet()) {
            if (!supportedOptions.contains(key)) {
                throw new IllegalArgumentException("不支持的参数: " + key);
            }
        }
        return Map.copyOf(options);
    }

    private static String requiredOption(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数 " + name);
        }
        return value;
    }

    private static void printHelp(PrintStream output) {
        output.println("Explicit Architecture Atlas CLI");
        output.println("用法：atlas extract --workspace <workspace.yaml> --package-id <stable-id> [--source-revision <revision>]");
        output.println("      atlas observe --workspace <workspace.yaml> --package-id <existing-package-id>");
        output.println("extract 读取私有 Workspace、编译期 Intent Manifest 和源码，生成可离线保存的 Atlas Package。");
        output.println("observe 读取 Workspace 私有目录中的脱敏 Axon 观测，生成独立且可过期的 Runtime Overlay。");
    }
}
