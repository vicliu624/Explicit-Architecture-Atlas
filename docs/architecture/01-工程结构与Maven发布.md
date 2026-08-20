# 工程结构与 Maven 发布

## 结论

Atlas 采用“一个可复用职责，一个 Maven 工件”的策略。这样分析一个严格 Explicit Architecture 项目时，使用者可以只引入自己需要的能力，例如仅使用注解与编译处理器、仅使用 JVM 源码分析器，或在服务端组合多个适配器；不会被 Web、Spring、Axon 或某个特定业务系统强制绑定。

Web 前端、测试样例、文档与本地 Workspace 不属于 Maven 发布物。它们是产品交付、验证和部署所需的仓库内容，但不应成为他人 Java 工程的传递依赖。

## 顶层目录

```text
Explicit-Architecture-Atlas/
├─ pom.xml                                  # Reactor：只聚合模块，不承担业务逻辑
├─ atlas-parent/                            # 发布的 Parent POM：JDK、插件、测试基线
├─ atlas-bom/                               # 发布的 BOM：锁定可组合工件版本
├─ atlas-contract/                          # 发布的 JAR：版本化 JSON Schema
│
├─ domain-layer/
│  ├─ atlas-domain-model/                   # 发布的 JAR：资产、约束、证据等稳定模型
│  └─ atlas-domain-testkit/                 # 发布的 JAR：构造领域测试数据的辅助工具
├─ application-layer/
│  ├─ atlas-application-api/                # 发布的 JAR：用例、查询、仓储端口
│  └─ atlas-application-core/               # 发布的 JAR：用例编排实现
├─ core/
│  └─ atlas-core/                            # 发布的 JAR：仅依赖 Atlas 内层模型的嵌入式 Facade
│
├─ infra-driven/                            # 被动适配器：Atlas 主动读取的外部技术事实
│  ├─ atlas-analysis-spi/                   # 发布的 JAR：分析器插件端口
│  ├─ atlas-source-jvm/                     # 发布的 JAR：JVM 源码事实提取器
│  ├─ atlas-maven-source/                   # 发布的 JAR：Maven Reactor/依赖事实提取器
│  ├─ atlas-spring-source/                  # 发布的 JAR：Spring Endpoint/调用事实提取器
│  ├─ atlas-axon-source/                    # 发布的 JAR：Axon 事件、Saga、投影事实提取器
│  ├─ atlas-axon-runtime/                   # 发布的 JAR：Axon 运行态观测适配器
│  ├─ atlas-jpa-target-resolver/             # 发布的 JAR：投影写入目标解析器
│  ├─ atlas-source-archive/                 # 发布的 JAR：src.jar / ZIP 源码读取器
│  ├─ atlas-file-store/                     # 发布的 JAR：本地 Workspace 路径与快照存储适配器
│  └─ atlas-content-store/                  # 发布的 JAR：人工内容包路径与读取适配器
│
├─ infra-driving/                           # 主动适配器：用户或构建工具如何启动 Atlas
│  ├─ atlas-annotations/                    # 发布的 JAR：零框架依赖的 SOURCE 注解 API
│  ├─ atlas-annotation-processor/           # 发布的 JAR：编译时意图索引处理器
│  ├─ atlas-maven-plugin/                   # 发布的 Maven Plugin：在 target/atlas 写出通用静态 Package
│  ├─ atlas-cli/                            # 发布的 JAR：JVM/Maven 通用命令行入口
│  ├─ atlas-cli-full/                       # 发布的 JAR：显式全适配器 fat JAR 分发
│  ├─ atlas-starter-jvm/                    # 发布的 POM：JVM/Maven 通用适配器组合
│  ├─ atlas-starter-spring/                 # 发布的 POM：显式 Spring 适配器组合
│  ├─ atlas-starter-axon/                   # 发布的 POM：显式 Axon 适配器组合
│  ├─ atlas-starter-jpa/                    # 发布的 POM：显式 JPA 适配器组合
│  └─ atlas-starter-full/                   # 发布的 POM：显式全适配器组合
│
├─ ui-layer/
│  ├─ atlas-http-api/                       # 发布的 JAR：HTTP 查询边界，不绑定 Web 框架
│  ├─ atlas-server-bootstrap/               # 发布的 JAR：服务端组合根
│  └─ atlas-web/                            # 独立 Node/Web 工程，不发布到 Maven
│
├─ integration-test/
│  ├─ atlas-architecture-conformance/       # 不发布：架构边界测试
│  ├─ atlas-integration-tests/              # 不发布：模块协作测试
│  ├─ fixture-projects/                     # 不发布：通用严格 Explicit Architecture 示例工程
│  └─ fixture-packs/                        # 不发布：示例内容包
├─ workspace/                               # 运行配置边界；workspace/local/ 被 Git 忽略
├─ docs/                                    # 中文架构、使用与决策文档
├─ deploy/                                  # 可公开复用的部署模板和说明
└─ scripts/                                 # 可在 CI 和本机运行的校验脚本
```

目录层次表达的是 Atlas 的依赖方向，不是被分析系统必须照抄的包名或模块名。被分析项目只要能提供足够的显式意图与技术证据，就可以使用 Atlas。

## 发布矩阵

| 类型 | Maven 发布 | 原因 | 消费者示例 |
| --- | --- | --- | --- |
| `atlas-parent` | 是，`pom` | 统一 Java、测试、插件与 Enforcer 基线 | Atlas 扩展模块维护者 |
| `atlas-bom` | 是，`pom` | 让使用者一次锁定互相兼容的 Atlas 版本 | Server/CLI 组合工程 |
| `atlas-contract` | 是，`jar` | Schema 可随依赖分发并独立版本化 | 导入器、渲染器、外部集成 |
| 领域模型与应用 API | 是，`jar` | 可作为稳定二进制/源码 API 复用 | 适配器、测试、服务端 |
| `atlas-application-core` | 是，`jar` | 可由不同驱动方式调用的用例编排 | CLI、HTTP Server |
| 分析 SPI 与每个技术适配器 | 是，`jar` | 让 Spring、Axon、JPA 等依赖保持可选 | 仅需要该技术栈的部署 |
| 注解、处理器、Maven Plugin、CLI、Starter | 是，`jar`/`maven-plugin`/`pom` | 供被分析工程的构建过程直接使用；Starter 显式选择技术适配器 | 被分析项目的构建、CI |
| HTTP API / Server Bootstrap | 是，`jar` | 支持不同 Web 运行时的组合 | 自建 Atlas 服务 |
| `atlas-web` | 否 | 是角色化 UI，不应成为 Java 传递依赖 | 浏览器/CDN/容器镜像 |
| 测试、fixture、docs、scripts | 否 | 只用于质量验证与仓库维护 | 贡献者、CI |
| `workspace/local` | 否且禁止提交 | 可能含真实源码、凭据、运行态与业务内容 | 单个部署环境 |

当前统一坐标如下：

```xml
<groupId>io.github.vicliu624.explicitarchitecture</groupId>
<version>0.0.1</version>
```

各工件使用 `explicit-architecture-atlas-` 前缀，例如 `explicit-architecture-atlas-domain-model`。这一命名避免与被分析项目的领域模块混淆，并使 Maven 搜索结果可识别。

## Parent、BOM 与 Reactor 的不同职责

它们很容易被混为一谈，但职责严格分开：

| 项目 | 解决的问题 | 是否传递业务/技术依赖 |
| --- | --- | --- |
| 根 `pom.xml` | 本仓库有哪些模块、以何顺序构建 | 否；只在源码仓库内聚合，`deploy` 已显式跳过它 |
| `atlas-parent` | Java 21、编译器、Surefire、Enforcer 等构建基线 | 否；提供构建约定 |
| `atlas-bom` | 一组互相兼容 Atlas JAR 的版本 | 否；只在 `dependencyManagement` 管理版本 |

外部使用者通常导入 BOM，而扩展 Atlas 本身的模块继承 Parent。根 Reactor 不应被其他项目继承或发布为公共“万能依赖”；`atlas-parent` 自身不依赖该聚合根，因此可以作为独立发布 Parent POM 被外部工程继承。

## 典型依赖方式

仅使用意图注解与处理器的项目可以这样配置：

```xml
<dependency>
  <groupId>io.github.vicliu624.explicitarchitecture</groupId>
  <artifactId>explicit-architecture-atlas-annotations</artifactId>
  <version>${atlas.version}</version>
</dependency>
```

处理器应放在 `maven-compiler-plugin` 的 `annotationProcessorPaths` 中，而不是变成应用运行时依赖。仓库中的 `integration-test/fixture-projects/strict-explicit-java-sample` 是可执行的完整例子。

服务端组合时先导入 BOM，再选择需要的适配器：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.vicliu624.explicitarchitecture</groupId>
      <artifactId>explicit-architecture-atlas-bom</artifactId>
      <version>${atlas.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

这样引入 `atlas-axon-source` 不会自动引入 Web；引入 `atlas-web` 也不可能意外把 Node 生态塞进 Java 的依赖图。

## 新模块的放置判断

新增功能前先回答两个问题：

1. 这个能力能否被其他部署方式、其他项目或其他分析器复用？能，则它应是一个 Maven 工件；不能，则考虑放入组合根、Web 或测试目录。
2. 它是在“读取外部事实”，还是“接收用户/构建工具的启动动作”？前者放 `infra-driven`，后者放 `infra-driving`。例如读取 `@PostMapping` 放 Spring Source；执行 `atlas:extract` 放 Maven Plugin。

如果一个模块需要同时知道 Spring、Axon、JPA、文件系统和 React，它几乎一定放错位置了。应先抽出应用端口或 SPI，再由组合根装配。
