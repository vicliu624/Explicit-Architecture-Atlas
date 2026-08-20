package io.github.vicliu624.explicitarchitecture.atlas.cli;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasWorkspace;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeOverlayCompileRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.RuntimeSignalProvider;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultConfigurationScenarioResolver;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultDesignAtlasResolver;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultRuntimeOverlayCompiler;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasPackage;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConfigurationScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeAssetHealth;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHealthStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignal;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeSignalBatch;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
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
import java.util.ServiceLoader;
import java.time.Instant;
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
                case "scenario" -> scenario(arguments.subList(1, arguments.size()), output);
                default -> {
                    error.println("未知或暂未实现的 Atlas 命令：" + arguments.getFirst());
                    printHelp(error);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            error.println("Atlas 命令执行失败：" + exception.getMessage());
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
                installedAnalyzers(),
                new DefaultDesignAtlasResolver())
                .build(new AtlasBuildRequest(
                        AtlasId.of(packageId),
                        workspace.project().id(),
                        workspace.sources().projectRoot(),
                        sourceRevision,
                        conventionProfile,
                        intents,
                        ContentBundle.empty(),
                        workspace.sources().sourceRoots(),
                        workspace.sources().excludedPaths(),
                        scenarios(workspace, sourceRevision)));

        output.println("Atlas Package 已生成：" + repository.fileOf(atlasPackage.manifest().packageId()));
        output.println("项目：" + atlasPackage.manifest().projectId() + "；节点：" + atlasPackage.designAtlas().nodes().size()
                + "；关系：" + atlasPackage.designAtlas().relations().size() + "；事实：" + atlasPackage.facts().facts().size());
        atlasPackage.manifest().coverage().forEach(coverage -> output.println(
                "覆盖：" + coverage.analyzerId() + " = " + coverage.status() + "（" + coverage.summary() + "）"));
        return 0;
    }

    /** 解析 Package 中已声明的命名场景；不读取操作系统环境变量，也不启动被分析的 Spring 应用。 */
    private static int scenario(List<String> arguments, PrintStream output) {
        Map<String, String> options = options(arguments, List.of("--workspace", "--package-id", "--scenario-id"));
        Path workspaceFile = Path.of(requiredOption(options, "--workspace"));
        AtlasWorkspace workspace = new AtlasWorkspaceYamlReader().read(workspaceFile);
        AtlasWorkspacePaths workspacePaths = new AtlasWorkspacePaths(workspaceFile.toAbsolutePath().normalize().getParent());
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(workspacePaths);
        AtlasPackage atlasPackage = repository.findById(AtlasId.of(requiredOption(options, "--package-id")))
                .orElseThrow(() -> new IllegalArgumentException("找不到 Atlas Package: " + requiredOption(options, "--package-id")));
        AtlasId scenarioId = AtlasId.of(requiredOption(options, "--scenario-id"));
        ConfigurationScenario configuration = atlasPackage.designAtlas().nodes().stream()
                .filter(ConfigurationScenario.class::isInstance).map(ConfigurationScenario.class::cast)
                .filter(candidate -> candidate.id().equals(scenarioId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Package 中没有命名配置场景: " + scenarioId));
        var resolution = new DefaultConfigurationScenarioResolver().resolve(atlasPackage, configuration);
        long active = resolution.components().stream().filter(component -> component.status().name().equals("ACTIVE")).count();
        long unresolved = resolution.components().stream().filter(component -> component.status().name().equals("UNRESOLVED")).count();
        output.println("配置场景：" + configuration.name() + "（" + configuration.id() + "）");
        output.println("活动 Profile：" + String.join(", ", configuration.activeProfiles()));
        output.println("组件：活动=" + active + "，未决=" + unresolved + "，总数=" + resolution.components().size());
        resolution.components().forEach(component -> output.println("组件 " + component.componentId() + " = " + component.status()
                + (component.reason().isBlank() ? "" : "；" + component.reason())));
        resolution.injections().forEach(injection -> output.println("注入 " + injection.injectionPointId() + " = " + injection.status()
                + "；候选=" + injection.candidates() + "；选择=" + injection.selected()
                + (injection.reasons().isEmpty() ? "" : "；" + String.join("；", injection.reasons()))));
        resolution.invocations().forEach(invocation -> output.println("调用 " + invocation.callerCodeId() + " -> "
                + invocation.staticTargetCodeId() + " = " + invocation.status()
                + "；接收者注入=" + invocation.receiverInjectionPointIds()
                + "；Bean 候选=" + invocation.candidateComponents()
                + "；Bean 选择=" + invocation.selectedComponents()
                + "；有效实现=" + invocation.effectiveTargetCodeIds()
                + "；声明 Advisor=" + invocation.declaredAdvisorIds()
                + (invocation.reasons().isEmpty() ? "" : "；" + String.join("；", invocation.reasons()))));
        resolution.diagnostics().forEach(diagnostic -> output.println("场景诊断：" + diagnostic.code() + "（" + diagnostic.message() + "）"));
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
        var signalBatch = runtimeSignals(runtime);
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

    /**
     * 从 classpath 上的 framework adapter 发现分析器；核心 CLI 不硬编码 Spring/Axon/JPA。
     * 插件缺失表示能力未安装，而不是被分析工程不存在该框架事实。
     */
    private static List<SourceFactsAnalyzer> installedAnalyzers() {
        List<SourceFactsAnalyzer> analyzers = ServiceLoader.load(SourceFactsAnalyzer.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(
                        analyzer -> analyzer.descriptor().id(),
                        analyzer -> analyzer,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .sorted(java.util.Comparator.comparing(analyzer -> analyzer.descriptor().id()))
                .toList();
        if (analyzers.isEmpty()) {
            throw new IllegalStateException("未发现已安装的 Atlas 分析器；请显式引入 atlas-starter-jvm 或所需框架 adapter");
        }
        return analyzers;
    }

    /** 将多个可选运行态适配器的脱敏信号合并为一批通用输入。 */
    private static RuntimeSignalBatch runtimeSignals(io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceRuntime runtime) {
        Map<AtlasId, RuntimeSignalProvider> providers = ServiceLoader.load(RuntimeSignalProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(RuntimeSignalProvider::id, provider -> provider, (first, ignored) -> first,
                        java.util.LinkedHashMap::new));
        List<RuntimeSignalProvider> selected = runtime.providers().isEmpty()
                ? providers.values().stream().sorted(java.util.Comparator.comparing(RuntimeSignalProvider::id)).toList()
                : runtime.providers().stream().map(AtlasId::of).map(id -> Optional.ofNullable(providers.get(id))
                        .orElseThrow(() -> new IllegalArgumentException("Workspace 请求的运行态 provider 未安装: " + id)))
                        .sorted(java.util.Comparator.comparing(RuntimeSignalProvider::id)).toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("未发现已安装的 Runtime Signal Provider；请显式引入所需运行态 adapter");
        }
        List<RuntimeSignalBatch> batches = selected.stream().map(provider -> provider.read(runtime)).toList();
        List<RuntimeSignal> signals = batches.stream().flatMap(batch -> batch.signals().stream()).toList();
        Instant capturedAt = batches.stream().map(RuntimeSignalBatch::capturedAt).max(Instant::compareTo).orElseThrow();
        return new RuntimeSignalBatch(capturedAt, signals);
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

    private static List<ConfigurationScenario> scenarios(AtlasWorkspace workspace, String sourceRevision) {
        return workspace.scenarios().stream().map(scenario -> {
            String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(scenario.scenarioId().value().getBytes(StandardCharsets.UTF_8));
            Evidence evidence = new Evidence(
                    AtlasId.of("evidence:workspace-scenario:" + encoded),
                    EvidenceOrigin.WORKSPACE_CONFIGURATION,
                    EvidenceClaim.DECLARED,
                    EvidenceStatus.VERIFIED,
                    new SourceLocator("generated://atlas/workspace/scenario/" + encoded, scenario.scenarioId().value(), SourceRange.unknown()),
                    sourceRevision,
                    EvidenceVisibility.EXPERT_ONLY,
                    "atlas-cli-workspace",
                    "由私有 Workspace 声明；redacted 属性不含明文值");
            return new ConfigurationScenario(
                    new AssetMetadata(scenario.scenarioId(), scenario.name(), Map.of(
                            "workspaceId", io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue.text(workspace.workspaceId().value())),
                            List.of(evidence)),
                    scenario.activeProfiles(), scenario.properties(), scenario.classpathCapabilities(),
                    scenario.classpathCapabilityCompleteness(), scenario.applicationContext());
        }).toList();
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
        output.println("      atlas scenario --workspace <workspace.yaml> --package-id <existing-package-id> --scenario-id <stable-id>");
        output.println("extract 读取私有 Workspace、编译期 Intent Manifest 和源码，生成可离线保存的 Atlas Package。");
        output.println("observe 通过 classpath 中已选择的 Runtime Provider 读取私有脱敏观测，生成独立且可过期的 Runtime Overlay。");
        output.println("scenario 只解析 Package 内已声明并脱敏的命名配置场景，输出有效 Bean、注入选择、场景调用分派和未决原因。");
    }
}
