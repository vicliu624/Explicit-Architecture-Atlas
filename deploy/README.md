# 部署边界

Atlas 的发布物分为两类：

1. Maven 发布物：父 POM、BOM、领域模型、应用 API、SPI、各技术适配器、注解、处理器、Maven 插件、CLI、HTTP 边界与服务器组合根；
2. 运行部署物：独立 Web 静态资源、服务端启动配置、私有 Workspace、业务内容包和运行态连接信息。

`deploy/` 只保存可公开复用的部署模板和说明。任何包含真实代码路径、业务数据、凭据或生产连接地址的配置必须放在 `workspace/local/` 或外部密钥系统，不能提交到仓库，也不能进入 Maven 产物。

## 0.0.1 起的 Maven 发布

`0.0.1` 已发布到 [Maven Central](https://search.maven.org/search?q=g%3Aio.github.vicliu624.explicitarchitecture)，这是普通 Java 消费者的默认渠道：无需在 POM 中声明额外 repository，也无需 token。GitHub Packages Maven Registry 仍保留为 GitHub 生态中的并行分发渠道，但不是使用 Central 坐标的前置条件。

所有公开模块使用统一坐标前缀：

```text
groupId:    io.github.vicliu624.explicitarchitecture
artifactId: explicit-architecture-atlas-<module>
version:    0.0.1
```

根 Reactor POM、fixture 和集成测试模块明确跳过部署；`release` Profile 为发布的 JAR 附加 source JAR 与 Javadoc JAR。`atlas-parent` 的 `distributionManagement` 仍统一声明 GitHub Packages 地址，供 tag 驱动的 GitHub Packages 发布；Maven Central 由独立、受保护的 `central-release` Profile 上传 bundle。

`explicit-architecture-atlas-cli-full` 的主工件就是可执行的 shaded JAR；不需要也不应猜测一个额外的 `all` classifier。`atlas-contract` 则以 schema 资源和包级 API 文档为公开契约，两者在发布时都带有对应的 sources/Javadocs。

发布由 `.github/workflows/release.yml` 管理，且只在推送 `v<version>` tag 时触发：

1. 签出 tag 对应的不可变提交，并验证 tag 与根 Maven 版本完全一致；
2. 使用 Actions 为本仓库自动签发的短期 `GITHUB_TOKEN` 配置 Maven `github` server；
3. 执行 `mvn -B -ntp -Prelease deploy`；
4. 在空 Maven 本地仓库中重新从 GitHub Packages 解析已发布的注解工件；
5. 所有 Maven 工件发布成功且消费者解析校验通过后，创建同名 GitHub Release 并生成发布说明。

工作流要求 `contents: write` 和 `packages: write`，权限已经显式写在 YAML 中。CI 和 PR 不具备发布权限，普通提交也不能意外部署版本。

若 tag 触发的发布已经成功上传 Maven 工件、但在随后解析验证或 GitHub Release 创建步骤失败，修复 workflow 后可以从 `main` 手动运行 `Release`，输入同一已有 tag。这个恢复入口只会重新校验 tag、在空本地仓库解析已发布工件并补建 GitHub Release，**不会再次执行 deploy**；不要强推 tag 或试图重发同一个不可变版本。

### 从 Maven Central 消费

普通消费者不需要 `<repositories>`、`settings.xml` 或 token。导入 BOM 后按需选择模块：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.vicliu624.explicitarchitecture</groupId>
            <artifactId>explicit-architecture-atlas-bom</artifactId>
            <version>0.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
        <artifactId>explicit-architecture-atlas-annotations</artifactId>
    </dependency>
</dependencies>
```

`explicit-architecture-atlas-annotations:0.0.1` 的 POM、主 JAR、source JAR、Javadoc JAR 与 PGP 签名均已发布。其他模块遵循相同的 groupId 和版本约定；选择模块时应遵守各自的依赖边界，而不是无差别引入 full starter。

### 从 GitHub Packages 消费（可选）

GitHub Packages Maven Registry 对下载也要求 classic Personal Access Token。消费者应以环境变量保存仅有 `read:packages` 权限的 token：

```xml
<!-- ~/.m2/settings.xml，绝不能提交到版本库 -->
<server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
</server>
```

在消费者 POM 中声明仓库后，可以导入 BOM：

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/vicliu624/Explicit-Architecture-Atlas</url>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.vicliu624.explicitarchitecture</groupId>
            <artifactId>explicit-architecture-atlas-bom</artifactId>
            <version>0.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Maven Central 的发布边界

GitHub Packages 与 Maven Central 是两个独立的发布目标。`0.0.1` 已成功发布到两者；普通消费者应优先使用 Maven Central。后续每个 Central 版本仍必须通过独立、受保护的发布流程，不能因为 GitHub Packages 中已有同版本而跳过签名、验证或审批。

首次 Central 发布前必须完成以下一次性准备（`0.0.1` 已完成）：

1. 使用 GitHub 身份登录 Central Portal，验证个人命名空间 `io.github.vicliu624`。本项目坐标 `io.github.vicliu624.explicitarchitecture` 是该命名空间的子空间；
2. 创建并安全保存 Central Portal 的 publishing token（用户名和密码）；
3. 创建可公开验证的 PGP **主**签名密钥，并确保对应公钥已发布；不要使用只有子签名密钥的配置；
4. 在 GitHub 仓库的 `maven-central` Environment 中设置下列受保护 Secrets，绝不提交它们：

   | Secret | 作用 |
   | --- | --- |
   | `CENTRAL_USERNAME` | Central Portal token 用户名 |
   | `CENTRAL_PASSWORD` | Central Portal token 密码 |
   | `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored 的 PGP 主私钥 |
   | `MAVEN_GPG_PASSPHRASE` | 仅用于 CI 批处理签名的私钥口令 |

`.github/workflows/publish-central.yml` 只能手动选择一个已有、带注释的 `v<version>` tag。它会：

1. 校验 tag、根 Maven 版本和 Git 注释 tag 完全一致；
2. 启用 `release,central-release` Profile，拒绝 SNAPSHOT 项目版本和依赖；
3. 为每个发布文件生成 source JAR、Javadoc JAR、PGP `.asc` 签名和校验和；
4. 使用 `central-publishing-maven-plugin` 上传并自动发布 Central Portal bundle，等待状态变为 `published`；
5. 在一个空 Maven 本地仓库中从默认 Maven Central 重新解析 `explicit-architecture-atlas-annotations`，作为最终消费者验证。

Central 已发布版本不可修改或删除。发现缺陷后必须发布新的语义化补丁版本，不能重推同一版本。首次运行前应确认 GitHub 的 `maven-central` Environment 已设置审批规则；工作流通过该 Environment 获取 token 与签名材料，普通 CI、PR 和 tag 发布流程永远看不到这些 Secret。

权威要求与配置细节参见 [Central 发布要求](https://central.sonatype.org/publish/requirements/)、[Central Portal Maven 插件](https://central.sonatype.org/publish/publish-portal-maven/) 和 [命名空间注册](https://central.sonatype.org/register/namespace/)。
