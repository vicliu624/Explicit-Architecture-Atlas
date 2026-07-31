package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 可离线保存、可服务端查询、可供多角色渲染的完整 Atlas 交付单元。
 *
 * <p>设计图谱、原始事实、显式意图、人类内容、诊断与运行态 Overlay 都有各自的边界，防止后续能力以破坏性方式扩展。</p>
 */
public record AtlasPackage(
        AtlasPackageManifest manifest,
        AtlasGraph designAtlas,
        FactBundle facts,
        IntentBundle intents,
        ContentBundle content,
        Optional<RuntimeOverlay> runtimeOverlay,
        List<AtlasDiagnostic> diagnostics) {

    public AtlasPackage {
        Objects.requireNonNull(manifest, "manifest 不能为空");
        Objects.requireNonNull(designAtlas, "designAtlas 不能为空");
        Objects.requireNonNull(facts, "facts 不能为空");
        Objects.requireNonNull(intents, "intents 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        runtimeOverlay = Objects.requireNonNull(runtimeOverlay, "runtimeOverlay 不能为空");
        Objects.requireNonNull(diagnostics, "diagnostics 不能为空");
        Map<AtlasId, AtlasDiagnostic> diagnosticsById = new LinkedHashMap<>();
        for (AtlasDiagnostic diagnostic : diagnostics) {
            diagnostic = Objects.requireNonNull(diagnostic, "诊断不能为空");
            if (diagnosticsById.putIfAbsent(diagnostic.id(), diagnostic) != null) {
                throw new IllegalArgumentException("诊断身份重复: " + diagnostic.id());
            }
        }
        diagnostics = List.copyOf(diagnosticsById.values().stream()
                .sorted(java.util.Comparator.comparing(AtlasDiagnostic::id)).toList());
    }
}
