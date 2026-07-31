# ADR-0001：按可复用职责划分 Maven 工件

- 状态：已接受
- 日期：2026-07-31
- 决策者：Explicit Architecture Atlas 维护者

## 背景

Atlas 同时需要稳定领域模型、数据契约、源码提取、Spring/Axon/JPA 集成、编译期注解、Maven 插件、CLI、HTTP 服务和 Web 界面。把这些能力放进单一 Jar 看似能减少目录数量，却会使所有用户被迫携带全部框架依赖，也无法独立发布/升级某个分析器。

此外，Atlas 分析的是外部 Explicit Architecture 项目。产品自身必须保持中立，不能把某个被分析业务系统的源码、路径、术语或基础设施依赖嵌入公共发行物。

## 决策

采用 Maven multi-module Reactor，并按可独立复用和独立版本兼容的职责拆分工件：

- `atlas-parent` 与 `atlas-bom` 作为发布的构建/版本入口；
- `atlas-contract`、领域模型、应用 API/Core 组成框架无关的内核；
- 每个外部技术来源（JVM、Maven、Spring、Axon、JPA、运行态、文件、内容）各自成为可选被动适配器；
- 注解 API、编译处理器、Maven Plugin、CLI 与 HTTP 边界作为独立主动适配器；
- `atlas-server-bootstrap` 作为唯一组合根；
- `atlas-web`、测试、fixture、文档、脚本和 Workspace 不发布到 Maven；
- 任何真实项目资料只进入被 Git 忽略的 `workspace/local/` 或外部安全系统。

发布使用统一 groupId `io.github.vicliu624.explicitarchitecture` 和统一工件前缀 `explicit-architecture-atlas-`。BOM 管理共同版本，使用者按需导入工件。

## 后果

正面后果：

- 纯 Java、Spring、Axon 等使用者只获取需要的依赖；
- 可以独立增加/替换技术分析器，而不破坏领域模型或 Web；
- 注解 API 保持零框架依赖，避免被分析项目的生产运行时增加负担；
- 领域模型与 Schema 可被 CLI、Server、IDE、CI 或第三方工具平等消费；
- Web 可以按浏览器生态发布，不把 Node 依赖带入 Maven；
- 私有项目资料有明确不入库、不入包的边界。

代价：

- Reactor 模块数较多，需要 Parent、BOM、结构脚本和文档保持一致；
- 跨模块改动必须处理 API/Schema 兼容性；
- Server 的组合代码需要显式选择和装配适配器；
- 需要持续的 conformance test，防止框架依赖反向渗透到内层。

这些代价是可接受的，因为 Atlas 的长期价值恰恰在于能面向多种 Explicit Architecture 项目和多种部署方式复用。

## 被否决的方案

### 单一 `atlas-all` Jar

被否决，因为它会把 Spring、Axon、JPA、Maven Plugin、CLI 与潜在 Web/Server 依赖混在一起。任何轻量消费者都会承担不需要的传递依赖，框架版本冲突也会迅速增加。

### 仅按技术语言目录拆分、不发布子工件

被否决，因为目录无法形成真正的版本、依赖与兼容性边界。外部项目无法只使用注解或只使用某一个分析器。

### 将 Web 打包进 Server Jar

被否决，因为 UI 的发布、缓存、安全策略和依赖生态与 Java Server 不同；更重要的是，Web 应消费契约化 Atlas 数据，而不是认识源码分析实现。

### 让真实业务项目成为 Reactor 子模块

被否决，因为 Atlas 是通用产品，不是某个业务系统的伴随模块。真实项目可以通过私有 Workspace、CI 或外部集成接入，但不得成为公开仓库和 Maven 发布链的一部分。
