# 扩展 Atlas

本页面向需要给 Atlas 增加技术分析器、数据字段、输入方式或部署能力的贡献者。核心原则是：**先扩展可证明事实的边界，再扩展渲染；不要把某个框架或项目私有信息推进领域核心。**

## 先判断新能力属于哪里

| 你要增加什么 | 放置位置 | 为什么 |
| --- | --- | --- |
| 新领域节点、关系、Evidence、运行态状态 | `domain-layer/atlas-domain-model` | 这是最内层、可发布、JDK-only 的稳定模型 |
| JSON Schema 或 Workspace 配置字段 | `atlas-contract` 与 application API | 这是跨工具和跨版本的数据边界 |
| 读取新技术事实，例如 Kafka、Camunda、另一种 ORM | `infra-driven/atlas-<technology>-source` | 外部系统被 Atlas 主动读取，是被动适配器 |
| 一个新的命令、IDE 入口、Gradle/Maven 输入方式 | `infra-driving/` | 外部调用 Atlas，是主动适配器 |
| Package → 业务/运维/专家解释 | `application-layer` | 应用层组装稳定读取模型，不能让适配器输出 Web DTO |
| HTTP/server/Web 页面 | `ui-layer` | 只负责协议、组合和渲染，不重新分析源码 |

## 新增事实分析器的标准步骤

1. 在 `infra-driven/` 创建独立模块，并只依赖 `atlas-analysis-spi` 与必要的技术 SDK；
2. 实现 `SourceFactsAnalyzer`，输出 `AnalyzerContribution`：Facts、关系、Evidence、诊断和 Coverage；
3. 无法唯一确认时输出 `PARTIAL` 或诊断，不生成猜测边；
4. 将适配器以可选方式装配到构建服务；未启用与“未发现”必须可区分；
5. 添加与真实业务项目无关的最小 fixture 和单元/集成测试；
6. 更新 Schema、Wiki、专题文档和发布矩阵。

例如，要支持一种新的消息平台，正确产物是“已证明的消费者、处理状态和受控证据”；不是把该平台的客户端对象、连接参数或原始消息塞进 `AtlasPackage`。

## 演进稳定数据时的检查表

新增一个字段或状态前，逐项回答：

- 它属于 Facts、Intent、Content 还是 Runtime Overlay？
- 它如何被 Evidence 支撑？可见范围是什么？
- 旧版本渲染器遇到它怎么办？是否需要 Schema 版本策略？
- 业务、运维、专家三个读取模型分别是否需要它？
- 它是否泄露路径、凭据、payload、异常文本或项目私有信息？
- fixture 是否同时覆盖成功、缺失、歧义和冲突情况？

领域模型不得依赖 Spring、Axon、JPA、Jackson 或 Web 类型；否则一个可发布的基础模型会把所有消费者锁死在同一技术版本。

## Maven Plugin 的扩展边界

`atlas-maven-plugin:extract` 已复用应用层构建用例，在当前 Maven 工程的 `target/atlas/packages/` 生成静态 Package。它自带 JVM/Maven 通用分析器，其他 adapter 由使用者以 Maven plugin dependency 显式加入，再通过 `ServiceLoader` 发现。这样给 Plugin 增加 Spring、Axon、JPA 或未来的技术适配器时，不需要在 Mojo 中复制任何分析逻辑，也不会让未选择框架成为传递依赖。

扩展 Maven Plugin 时应保持这一边界：新增参数只描述项目根、稳定身份、源码根、排除路径、意图清单、Convention Profile 和输出位置；静态分析仍实现为 `SourceFactsAnalyzer`，私有配置场景和运行态观测仍保留在受控 Workspace/CLI/Server 路径。完整配置示例见[本地开发、构建与发布](../contributors/01-本地开发、构建与发布.md#在被分析工程中使用-maven-plugin)。

## 完成一个扩展的最低验证

```powershell
mvn -B test
npm --prefix ui-layer/atlas-web run check
pwsh ./scripts/verify-structure.ps1
```

之后至少人工检查：新的 Coverage 是否正确、空结果是否不会伪装成“没有业务能力”、业务视图是否仍然不用技术术语、专家视图是否能追溯 Evidence。更详细的依赖规则见[依赖规则与装配](../architecture/02-依赖规则与装配.md)。
