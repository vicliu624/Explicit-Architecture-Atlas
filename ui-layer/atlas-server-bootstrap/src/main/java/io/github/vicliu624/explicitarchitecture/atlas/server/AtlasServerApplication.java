package io.github.vicliu624.explicitarchitecture.atlas.server;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasWorkspace;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasRoleViewQuery;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.RepositoryBackedAtlasPackageQuery;
import io.github.vicliu624.explicitarchitecture.atlas.http.AtlasHttpRouter;
import io.github.vicliu624.explicitarchitecture.atlas.source.archive.SourceArchiveReader;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasPackageFileRepository;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspacePaths;
import io.github.vicliu624.explicitarchitecture.atlas.store.file.AtlasWorkspaceYamlReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Atlas 查询服务的本地 / 内网启动入口。
 *
 * <p>默认仅开放角色化 JSON 查询。项目源码查看需要显式加上 {@code --enable-expert-source} 才会开放，并且仍只允许
 * 读取 Workspace projectRoot 或显式 sourceArchives 中、Package 已知资产对应的源码。</p>
 */
public final class AtlasServerApplication {

    private AtlasServerApplication() {
    }

    public static void main(String[] arguments) {
        if (List.of(arguments).contains("--help") || List.of(arguments).contains("help")) {
            printUsage();
            return;
        }
        try {
            Arguments parsed = Arguments.parse(List.of(arguments));
            try (AtlasJdkHttpServer server = start(parsed.workspaceFile(), parsed.port(), parsed.enableExpertSource())) {
                System.out.println("Atlas 查询服务已启动：http://127.0.0.1:" + server.port());
                System.out.println("角色视图：/api/v1/packages/{package-id}/business | /operations | /expert");
                System.out.println("场景解析：/api/v1/packages/{package-id}/scenarios/{scenario-id}");
                System.out.println(parsed.enableExpertSource()
                        ? "专家源码查看已启用，仅解析 Package 已知的项目源码；必要时再读取声明的 sourceArchives。"
                        : "专家源码查看未启用；使用 --enable-expert-source 可显式开启。");
                Thread.currentThread().join();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Atlas 查询服务启动失败：" + exception.getMessage());
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("用法：atlas-server --workspace <workspace.yaml> [--port <0-65535>] [--enable-expert-source]");
        System.out.println("默认仅开放角色化 JSON；--enable-expert-source 仅返回 Package 已知资产位于 Workspace projectRoot 的项目源码。\n"
                + "可选 sourceArchives 只作为找不到项目源码时的受控回退。");
    }

    static AtlasJdkHttpServer start(Path workspaceFile, int port, boolean enableExpertSource) {
        Path normalizedWorkspaceFile = workspaceFile.toAbsolutePath().normalize();
        AtlasWorkspace workspace = new AtlasWorkspaceYamlReader().read(normalizedWorkspaceFile);
        AtlasPackageFileRepository repository = new AtlasPackageFileRepository(
                new AtlasWorkspacePaths(normalizedWorkspaceFile.getParent()));
        var packageQuery = new CachingAtlasPackageQuery(new RepositoryBackedAtlasPackageQuery(repository));
        var roleViews = new DefaultAtlasRoleViewQuery(packageQuery);
        AtlasHttpRouter router = new AtlasHttpRouter(
                roleViews,
                enableExpertSource ? Optional.of(expertSourceQuery(packageQuery, workspace)) : Optional.empty(),
                new PackageBackedScenarioHttpQuery(packageQuery));
        return AtlasJdkHttpServer.start(port, router);
    }

    private static WorkspaceBackedExpertSourceHttpQuery expertSourceQuery(
            io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasPackageQueryUseCase packageQuery,
            AtlasWorkspace workspace) {
        return new WorkspaceBackedExpertSourceHttpQuery(
                packageQuery, workspace.sources().projectRoot(), new SourceArchiveReader(), workspace.sources().sourceArchives());
    }

    private record Arguments(Path workspaceFile, int port, boolean enableExpertSource) {

        private static Arguments parse(List<String> values) {
            Path workspace = null;
            Integer port = 8080;
            boolean enableExpertSource = false;
            for (int index = 0; index < values.size(); index++) {
                String option = values.get(index);
                switch (option) {
                    case "--workspace" -> {
                        if (++index >= values.size()) {
                            throw new IllegalArgumentException("缺少 --workspace 的值");
                        }
                        if (workspace != null) {
                            throw new IllegalArgumentException("--workspace 不能重复");
                        }
                        workspace = Path.of(values.get(index));
                    }
                    case "--port" -> {
                        if (++index >= values.size()) {
                            throw new IllegalArgumentException("缺少 --port 的值");
                        }
                        try {
                            port = Integer.parseInt(values.get(index));
                        } catch (NumberFormatException exception) {
                            throw new IllegalArgumentException("--port 必须是整数", exception);
                        }
                        if (port < 0 || port > 65535) {
                            throw new IllegalArgumentException("--port 必须在 0 到 65535 之间");
                        }
                    }
                    case "--enable-expert-source" -> enableExpertSource = true;
                    default -> throw new IllegalArgumentException("不支持的参数: " + option);
                }
            }
            if (workspace == null) {
                throw new IllegalArgumentException("缺少必填参数 --workspace");
            }
            return new Arguments(workspace, port, enableExpertSource);
        }
    }
}
