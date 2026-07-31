package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

/**
 * 从一个技术来源提取可验证事实的插件端口。
 *
 * <p>JVM、Maven、Spring、Axon、JPA 等分析器都只依赖此端口和领域模型，而不反向依赖 Web 或启动模块。</p>
 */
public interface SourceFactsAnalyzer {

    AnalyzerDescriptor descriptor();

    AnalyzerContribution analyze(AnalysisRequest request);
}
