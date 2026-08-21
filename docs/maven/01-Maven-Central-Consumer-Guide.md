# Maven Central 与 POM 完整使用手册

> **适用版本：`0.0.1`**
>
> **公共坐标前缀：`io.github.vicliu624.explicitarchitecture`**
>
> **权威目录：[`consumer-catalog.json`](consumer-catalog.json)**；CI 会把它与根 Reactor、BOM 和本页逐项交叉校验。
> 本页说明的是“消费者怎样使用已发布工件”，不是 Atlas 仓库内部模块的开发指南。

Explicit Architecture Atlas 发布的是一组刻意拆分的 Maven 工件，而不是一个会把 Spring、Axon、JPA、HTTP 和本地存储全部带入项目的单体 Jar。选择坐标本身就是架构选择：一个普通 Java 项目只获得 JVM/Maven 分析能力；只有明确选择框架适配器、Starter 或 full 分发物时，相应能力才会进入 classpath。

`0.0.1` 已发布至 [Maven Central](https://search.maven.org/search?q=g%3Aio.github.vicliu624.explicitarchitecture)。正常 Maven/Gradle 消费者无需添加 repository、用户名或 token。Atlas 工件使用 Java 21 编译；若将 `explicit-architecture-atlas-parent` 用作父 POM，它还会为扩展工程建立 Java 21、Maven 3.9+ 的构建基线。

## 先作选择：你需要哪种消费方式？

```text
要在业务源码中声明领域意图？ ──────────────> annotations + annotation-processor
要在 Maven 构建中生成 Atlas Package？ ──────> maven-plugin（可加 plugin dependency）
要在自己的 JVM 进程嵌入 Atlas？ ───────────> starter 或细粒度 library
要开发新的 Atlas 分析器/Provider？ ─────────> parent + analysis-spi（按需加 API）
只想运行一个命令行工具？ ─────────────────> cli 或 cli-full，java -jar
只想启动本地 HTTP 服务？ ─────────────────> server-bootstrap，java -jar
```

除非下面的“完整坐标目录”明确说是可执行分发物，否则请不要把它当作命令行 Jar；除非明确说是 Maven build plugin，否则请不要放进 `<build><plugins>`；除非明确说是 plugin dependency，否则请不要放进业务应用的 `<dependencies>`。

## 所有普通依赖的起点：导入 BOM

对于绝大多数 Atlas Jar，先在**消费项目**的 `pom.xml` 导入 BOM；之后在 `<dependencies>` 中选择需要的坐标，不写 `<version>`：

```xml
<properties>
    <atlas.version>0.0.1</atlas.version>
</properties>

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

`dependencyManagement` 只管理版本，**不会**把任何 Atlas 类、ServiceLoader 实现或框架依赖加入 classpath。真正启用能力仍须把对应 Jar 写进 `<dependencies>`、`annotationProcessorPaths`、`<plugins>` 或 Plugin 的 `<dependencies>`。

### BOM 的三个刻意例外

| 工件 | 为什么不由 BOM 管理 | 正确位置 |
| --- | --- | --- |
| `explicit-architecture-atlas-parent` | Parent 不是普通 dependency；根 Reactor 也不能作为外部父 POM。 | 项目根 `<parent>`，显式写版本。 |
| `explicit-architecture-atlas-bom` | 它是被 import 的管理表，不能管理自身版本。 | `<dependencyManagement>`，显式写版本、`type=pom`、`scope=import`。 |
| `explicit-architecture-atlas-maven-plugin` | Maven 不会用项目 BOM 的 dependencyManagement 给 build plugin 解析版本；Plugin 的专属依赖也有独立 classpath。 | `<build><plugins>`，显式写 `<version>0.0.1</version>`。 |

不要把项目根 `pom.xml` 或内部聚合 Parent 当作外部父 POM；对外可继承的只有 `explicit-architecture-atlas-parent`。

## POM 放置模型：不要把五种 classpath 混在一起

| 你正在配置的东西 | 应放的位置 | 典型工件 | 影响范围 |
| --- | --- | --- | --- |
| 业务/宿主应用在编译或运行时调用的 API | `<dependencies>` | `...-core`、`...-application-api`、Starter | 业务应用 classpath。 |
| 仅测试可见的帮助库 | `<dependencies><dependency><scope>test</scope>` | `...-domain-testkit` | 测试 classpath，不进入正式包。 |
| 编译期生成 Intent Manifest 的处理器 | `maven-compiler-plugin` 的 `annotationProcessorPaths` | `...-annotation-processor` | Javac 处理器路径，不作为应用 runtime 依赖。 |
| Maven 构建目标（消费者目录模式名：`maven-build-plugin`） | `<build><plugins>` | `...-maven-plugin` | Maven 构建进程；版本必须明确。 |
| 构建目标可发现的分析器 | Atlas Plugin 的 `<dependencies>`（**plugin dependency**） | `...-spring-source`、`...-axon-source`、`...-jpa-target-resolver` | 仅 Atlas Plugin 的构建期 classpath，不污染应用。 |
| 独立工具/服务 | 不写成业务 dependency；下载/发行后执行 | `...-cli`、`...-cli-full`、`...-server-bootstrap` | 单独 JVM，使用 `java -jar`。 |

## 完整坐标目录（全部 30 个公开工件）

下表是当前版本每一个可发布工件的消费契约。表中的“位置”优先级高于名称猜测；例如 `axon-runtime` 不是静态分析器，`http-api` 也不是可直接启动的 HTTP 服务。

### 构建与版本基线

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-parent`（`pom`） | 项目根 `<parent>`，版本 `0.0.1`。 | 你在开发 Atlas 扩展、适配器或希望采用 Atlas 的 Java/Maven 构建基线。 | 不作为 `<dependency>`，不继承 Atlas 根 Reactor。 |
| `explicit-architecture-atlas-bom`（`pom`） | `<dependencyManagement>` 的 import。 | 任何使用下列普通 Jar 的 Maven 项目。 | 不会自动引入能力，也不管理 Maven Plugin 本体。 |

外部扩展项目使用 Parent 的完整骨架：

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
        <artifactId>explicit-architecture-atlas-parent</artifactId>
        <version>0.0.1</version>
    </parent>
    <artifactId>my-atlas-extension</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</project>
```

### 稳定契约、领域与应用层

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-contract` | 普通 `<dependency>`。 | 需要稳定公共 contract、schema 资源或跨边界类型。 | 不把它误认为完整的应用服务实现。 |
| `explicit-architecture-atlas-domain-model` | 普通 `<dependency>`。 | 要使用 Atlas 的 JDK-only 领域模型。 | 不期待它携带 Spring/Axon/JPA。 |
| `explicit-architecture-atlas-domain-testkit` | `<dependency>` + `<scope>test</scope>`。 | 为领域模型消费者编写测试。 | 不应进入生产运行时或发布 API。 |
| `explicit-architecture-atlas-application-api` | 普通 `<dependency>`。 | 宿主要面对 Atlas 应用端口、Workspace 输入或角色视图 contract。 | 不把它当作可执行的用例实现。 |
| `explicit-architecture-atlas-application-core` | 普通 `<dependency>`，通常由 composition/Starter 带入。 | 自己显式组装 Atlas 用例的参考实现。 | 不是轻量接口包；无需求不要越过更稳定的 API。 |
| `explicit-architecture-atlas-core` | 普通 `<dependency>`，或使用 Starter 间接取得。 | 嵌入 Atlas 稳定 Facade，且需要保持框架无关。 | 不会自动发现 Spring/Axon/JPA；需要相应 Starter/adapter。 |

基础嵌入式依赖示例：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
        <artifactId>explicit-architecture-atlas-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
        <artifactId>explicit-architecture-atlas-application-api</artifactId>
    </dependency>
</dependencies>
```

### 注解与编译期 Intent Manifest

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-annotations` | 普通 `<dependency>`。 | 在业务/领域源码中写 Atlas 注解，显式表达操作、领域原语、业务规则、约束和意图。 | 它不是解释器，也不会单独生成 Manifest。 |
| `explicit-architecture-atlas-annotation-processor` | `maven-compiler-plugin` 的 `annotationProcessorPaths`。 | 编译时把注解编译成 `META-INF/explicit-architecture-atlas/atlas-intent.json`。 | 不要仅作为业务运行时 dependency；它不是运行时框架。 |

完整的注解 + 处理器配置如下。`annotations` 是源码编译所需 API；处理器则只在编译期间运行：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
        <artifactId>explicit-architecture-atlas-annotations</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.vicliu624.explicitarchitecture</groupId>
                        <artifactId>explicit-architecture-atlas-annotation-processor</artifactId>
                        <version>${atlas.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

处理器执行后检查：

```text
target/classes/META-INF/explicit-architecture-atlas/atlas-intent.json
```

没有这个文件时，后续分析仍可进行通用源码提取，但不能声称已捕获注解表达的业务规则或领域原语意图。

### 分析扩展点与细粒度源码适配器

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-analysis-spi` | 普通 `<dependency>`，通常供扩展实现编译。 | 编写自定义 Analyzer、Provider 或适配器并与 Atlas SPI 对接。 | 仅加 SPI 不会让实现被发现；还需实现、服务注册和宿主装配。 |
| `explicit-architecture-atlas-source-jvm` | 宿主普通 `<dependency>`，或 Maven Plugin 的 plugin dependency。 | 分析通用 JVM/Java 源码事实。 | 不分析 Maven POM 或框架语义。 |
| `explicit-architecture-atlas-maven-source` | 宿主普通 `<dependency>`，或 Plugin 的 plugin dependency。 | 分析 Maven POM、模块和依赖事实。 | 不替代 JVM 或框架分析器。 |
| `explicit-architecture-atlas-spring-source` | Starter，宿主普通 `<dependency>`，或 Plugin 的 plugin dependency。 | 需要 Spring Bean、条件、Profile、注入和 Endpoint 源码事实。 | 不把它加入不需要 Spring 分析的普通 CLI/项目。 |
| `explicit-architecture-atlas-axon-source` | Starter，宿主普通 `<dependency>`，或 Plugin 的 plugin dependency。 | 需要 Axon 命令、事件、处理器等源码事实。 | 不等同于运行态观测；运行态另选 `axon-runtime`。 |
| `explicit-architecture-atlas-jpa-target-resolver` | Starter，宿主普通 `<dependency>`，或 Plugin 的 plugin dependency。 | 需要解析 JPA 持久化/投影目标。 | 不应使核心或无 JPA 项目承受 JPA adapter。 |

构建期 Atlas Maven Plugin 自带 `source-jvm` 和 `maven-source`。要让同一个 `atlas:extract` 解析框架事实，把**确实需要**的适配器放进该 Plugin 的 dependencies：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.vicliu624.explicitarchitecture</groupId>
            <artifactId>explicit-architecture-atlas-maven-plugin</artifactId>
            <version>0.0.1</version>
            <dependencies>
                <!-- plugin dependency：仅构建期 Atlas Plugin 可见 -->
                <dependency>
                    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
                    <artifactId>explicit-architecture-atlas-spring-source</artifactId>
                    <version>0.0.1</version>
                </dependency>
                <dependency>
                    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
                    <artifactId>explicit-architecture-atlas-axon-source</artifactId>
                    <version>0.0.1</version>
                </dependency>
                <dependency>
                    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
                    <artifactId>explicit-architecture-atlas-jpa-target-resolver</artifactId>
                    <version>0.0.1</version>
                </dependency>
            </dependencies>
            <executions>
                <execution>
                    <goals><goal>extract</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

三个都写上只适合确实需要完整框架覆盖的项目；对单一 Spring 项目，只保留 `spring-source`。这种显式选择保证覆盖状态可审计，也避免构建/运行 classpath 被不相关框架污染。

### 运行时、存储和受控源码能力

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-axon-runtime` | 宿主普通 `<dependency>`，或 `starter-axon` 间接引入。 | 要导入/解释受控的 Axon 运行态观测、投影或流程管理健康度。 | 它不是 Axon 静态源码分析器，也不会启动业务 Axon 应用。 |
| `explicit-architecture-atlas-source-archive` | 宿主普通 `<dependency>`。 | 专家模式下受控读取本地源码归档。 | 不应自动上传、公开或无授权地暴露源码。 |
| `explicit-architecture-atlas-file-store` | 宿主普通 `<dependency>`，通常由服务器/Plugin 组合带入。 | 使用本地 Workspace、Atlas Package 的文件存储。 | 不替代组织的远程持久化/访问控制。 |
| `explicit-architecture-atlas-content-store` | 宿主普通 `<dependency>`。 | 接入受控叙事内容/内容包的本地存储边界。 | 不应假定 CLI 会自动导入任意业务手册。 |

这些是较低层的装配部件。若只想使用 Atlas，不应仅凭名字逐个引入它们；优先从 Starter、CLI 或 `server-bootstrap` 开始，仅在自己维护宿主组合根时细粒度选择。

### Maven Plugin、Starter 与分发物

| 坐标 | 位置 | 何时选择 | 不应这样使用 |
| --- | --- | --- | --- |
| `explicit-architecture-atlas-maven-plugin`（`maven-plugin`） | `<build><plugins>`，显式版本。 | 在 `package` 阶段或执行 `mvn atlas:extract` 时生成 Package。 | BOM 不会为它提供版本；不要放在应用 `<dependencies>`。 |
| `explicit-architecture-atlas-cli` | 独立下载后 `java -jar`。 | 仅需通用 JVM/Maven 分析的框架中立命令行。 | 不作为应用库或 Spring/Axon/JPA 分析器集合。 |
| `explicit-architecture-atlas-cli-full` | 独立下载后 `java -jar`。 | 明确需要全适配器的工具/演示环境。 | 不替代轻量 CLI；不应仅为“以后也许需要”而默认使用。 |
| `explicit-architecture-atlas-starter-jvm` | 普通 `<dependency>`。 | 一次选择 core + JVM + Maven 源码分析。 | 不含 Spring、Axon、JPA 专属适配器。 |
| `explicit-architecture-atlas-starter-spring` | 普通 `<dependency>`。 | 通用能力外还需要 Spring 源码分析。 | 不自动包含 Axon/JPA。 |
| `explicit-architecture-atlas-starter-axon` | 普通 `<dependency>`。 | 通用能力外还需要 Axon 源码及其 runtime overlay 能力。 | 不自动包含 Spring/JPA。 |
| `explicit-architecture-atlas-starter-jpa` | 普通 `<dependency>`。 | 通用能力外还需要 JPA 目标解析。 | 不自动包含 Spring/Axon。 |
| `explicit-architecture-atlas-starter-full` | 普通 `<dependency>`。 | 明确需要 Spring + Axon + JPA 的全量组合。 | 不作为默认 Starter。 |
| `explicit-architecture-atlas-http-api` | 宿主普通 `<dependency>`。 | 自己托管 HTTP server 时，需要 Atlas 的 host-neutral 路由与响应边界。 | 它不是独立 HTTP server，不能 `java -jar`。 |
| `explicit-architecture-atlas-server-bootstrap` | 独立下载后 `java -jar`。 | 直接运行本地 Atlas HTTP 服务组合。 | 不作为业务应用依赖，也不把它当 host-neutral HTTP contract。 |

Starter 的实际组合关系如下：

```text
starter-jvm    = core + source-jvm + maven-source
starter-spring = starter-jvm + spring-source
starter-axon   = starter-jvm + axon-source + axon-runtime
starter-jpa    = starter-jvm + jpa-target-resolver
starter-full   = starter-spring + starter-axon + starter-jpa
```

因此可按宿主的真实技术栈选择一个 focused Starter；如果既有 Spring 又有 JPA，写两个 focused Starter 是完全正确且更诚实的做法。只有三类适配器都需要时，才使用 `starter-full`。

## 可复制配方

### 配方 A：业务工程只增加可解释的领域意图

1. 导入 BOM。
2. 添加 `explicit-architecture-atlas-annotations` 为普通 dependency。
3. 在 `maven-compiler-plugin` 设置 `annotationProcessorPaths`，添加 `explicit-architecture-atlas-annotation-processor`。
4. 在 `mvn compile` 后检查 Intent Manifest 产物。

注解应描述系统自身拥有的业务语义，例如领域原语的约束、操作的输入/返回值含义、规则、状态或事件的业务名称。对来自 `CommonUtils` 一类领域层工具的约束，优先用注解表达其业务不变量；无法显式标注的现有规则仍可由源码事实与受控源码入口暴露，不能被注解“猜测”取代。

### 配方 B：在 CI 构建中生成 Package

1. 先完成配方 A（可选，但推荐）。
2. 在需要生成 Package 的模块配置 `explicit-architecture-atlas-maven-plugin`，**显式**写版本。
3. 需要框架事实时按上一节添加对应 plugin dependency。
4. 运行 `mvn package` 或 `mvn atlas:extract`，归档 `target/atlas`。

Plugin 的目标名是 `atlas:extract`；默认读取编译输出中的 Intent Manifest，并将结果写在 Maven `target/atlas`。多模块项目须明确决定“每个业务模块各生成 Package”还是“聚合模块显式列出 Manifest/源码根”，不要假设 Plugin 会魔法般扫描整个 Reactor。

```xml
<plugin>
    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
    <artifactId>explicit-architecture-atlas-maven-plugin</artifactId>
    <version>0.0.1</version>
    <configuration>
        <packageId>package:orders:1.4.0</packageId>
        <projectId>project:billing:orders</projectId>
        <sourceRevision>${project.version}</sourceRevision>
        <outputDirectory>target/atlas</outputDirectory>
        <sourceRoots>
            <sourceRoot>src/main/java</sourceRoot>
        </sourceRoots>
        <excludedPaths>
            <excludedPath>src/test</excludedPath>
        </excludedPaths>
    </configuration>
    <executions>
        <execution><goals><goal>extract</goal></goals></execution>
    </executions>
</plugin>
```

在 CI 中把 `sourceRevision` 设为不可变的 commit SHA，而不是时间戳或任意 working tree 字符串；把 `target/atlas` 与测试报告、二进制一道按构建版本归档。

### 配方 C：把 Atlas 嵌入自己的 Java 宿主

最常见的通用宿主只需要：

```xml
<dependency>
    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
    <artifactId>explicit-architecture-atlas-starter-jvm</artifactId>
</dependency>
```

Spring 宿主将其替换/提升为 `explicit-architecture-atlas-starter-spring`；Axon 宿主使用 `...-starter-axon`；需要 JPA 目标解析时使用 `...-starter-jpa`。如果你在维护自己的组合根而非简单嵌入，请按“细粒度源码适配器”和“运行时、存储”两节显式挑选 API、实现、存储和适配器，并为 ServiceLoader 实现保留可发现性。

### 配方 D：开发 Atlas 扩展或私有适配器

使用 `explicit-architecture-atlas-parent` 作为 Parent，再添加：

```xml
<dependency>
    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
    <artifactId>explicit-architecture-atlas-analysis-spi</artifactId>
</dependency>
```

按你的扩展职责再选择 `application-api`、`contract` 或细粒度 adapter。扩展实现需要遵守 SPI 的服务发现方式；仅声明 SPI dependency 不会自动把一个私有实现注册给 CLI、Plugin 或你的宿主。

### 配方 E：运行 CLI 或本地 HTTP 服务

这些是 shaded 主 Jar，应该下载到工具目录后直接执行，而不是作为业务工程依赖。PowerShell 示例：

```powershell
mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy `
  -Dartifact=io.github.vicliu624.explicitarchitecture:explicit-architecture-atlas-cli:0.0.1 `
  -DoutputDirectory=tools

java -jar tools/explicit-architecture-atlas-cli-0.0.1.jar --help
```

若要全适配器 CLI，只把 artifact 改为 `explicit-architecture-atlas-cli-full`。若要本地 HTTP 服务，只把 artifact 改为 `explicit-architecture-atlas-server-bootstrap`，再按服务帮助传入 Workspace/端口参数：

```powershell
java -jar tools/explicit-architecture-atlas-server-bootstrap-0.0.1.jar --help
```

`cli`、`cli-full` 和 `server-bootstrap` 是发布的可执行分发物；`http-api` 是给自定义宿主嵌入的边界库，二者不能互换。

### 配方 F：测试 Atlas 领域模型

```xml
<dependency>
    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
    <artifactId>explicit-architecture-atlas-domain-testkit</artifactId>
    <scope>test</scope>
</dependency>
```

如果同时需要领域类型，再添加 `explicit-architecture-atlas-domain-model` 为普通 dependency。测试工具不应经由生产模块向下游泄漏。

## 用依赖树验证边界

选择坐标后，不要只看编译是否通过。至少对正式模块检查：

```powershell
mvn -q dependency:tree `
  -Dincludes=io.github.vicliu624.explicitarchitecture
```

再按选择检查以下约束：

- 使用 `starter-jvm` 或 `cli` 时，不应无意看到 Spring、Axon、JPA adapter；
- 使用 `starter-spring` 时，不应因为这个选择而获得 Axon/JPA adapter；
- 用 Maven Plugin 时，Spring/Axon/JPA adapter 应在 Plugin 的 dependencies，不在业务应用的依赖树；
- 使用 Annotation Processor 时，`atlas-intent.json` 应在 `target/classes`，而不是依赖它存在于生产运行时；
- 运行可执行分发物时，使用 `java -jar`，不把 fat Jar 当作业务 SDK。

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 写了 BOM，Plugin 却提示没有版本 | Maven build plugin 不从项目 BOM 获取版本。 | 给 `explicit-architecture-atlas-maven-plugin` 显式写 `<version>0.0.1</version>`。 |
| Spring 工程没有 Bean/条件事实 | 只装了通用 Plugin/Starter，未选择 Spring adapter。 | 用 `starter-spring`，或将 `spring-source` 加为 plugin dependency。 |
| 加了 annotations，Package 没有意图 | 未配置/执行 Annotation Processor，或检查了错误模块。 | 配置 `annotationProcessorPaths` 并检查 `target/classes/.../atlas-intent.json`。 |
| Axon 静态关系与运行投影都没有 | `axon-source` 与 `axon-runtime` 是两种不同能力。 | 静态分析选 source adapter；需要受控运行观测再选 runtime/Starter。 |
| 想启动 `http-api` 却没有 main 类 | `http-api` 只定义 HTTP 边界。 | 使用 `server-bootstrap`，或自行创建宿主并嵌入该 API。 |
| 普通应用被不相关框架依赖污染 | 默认选择了 full 组合或误将 plugin dependency 放入应用。 | 回到 focused Starter 或将 adapter 移至 Plugin dependencies。 |

## 目录如何保持可信

[`scripts/verify-maven-consumer-catalog.ps1`](../../scripts/verify-maven-consumer-catalog.ps1) 会校验三件事：

1. 每个未跳过 deploy 的 Reactor 模块都在 `consumer-catalog.json` 中，且 packaging 一致；
2. BOM 管理的工件集合与目录的 `bomManaged` 标记完全一致；
3. 本页逐个提及所有已发布 artifactId，并包含 Parent、BOM、`annotationProcessorPaths`、Maven build plugin、plugin dependency、`java -jar` 等不能混淆的消费方式。

因此，新增、删除、改名或调整发布范围的工件时，必须同时更新 POM、目录和本页；CI 会拒绝“已发布但无使用说明”或“文档仍指导消费者使用不存在坐标”的变更。
