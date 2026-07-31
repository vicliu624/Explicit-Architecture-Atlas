package io.github.vicliu624.explicitarchitecture.atlas.analysis.spi;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;

import java.util.List;
import java.util.Objects;

/**
 * 单一分析器对 Atlas 编译器作出的事实贡献。
 *
 * <p>贡献不是最终业务图谱：它只报告本分析器可证明的事实、候选关系、诊断和覆盖范围，随后由应用核心归一化。</p>
 */
public record AnalyzerContribution(
        CoverageReport coverage,
        List<SourceFact> facts,
        List<FactRelation> relations,
        List<AtlasDiagnostic> diagnostics) {

    public AnalyzerContribution {
        Objects.requireNonNull(coverage, "coverage 不能为空");
        facts = List.copyOf(Objects.requireNonNull(facts, "facts 不能为空"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations 不能为空"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics 不能为空"));
    }

    public static AnalyzerContribution notImplemented(String analyzerId, String scopeUri) {
        return new AnalyzerContribution(
                new CoverageReport(analyzerId, CapabilityStatus.NOT_IMPLEMENTED, scopeUri, "分析器模块已安装，但提取能力尚未实现"),
                List.of(),
                List.of(),
                List.of());
    }
}
