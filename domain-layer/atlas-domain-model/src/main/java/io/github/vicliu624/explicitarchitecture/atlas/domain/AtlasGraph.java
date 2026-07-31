package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 设计时 Atlas 的类型化节点与关系图。
 *
 * <p>图是最终渲染、查询与离线审计的共同基础；它不存放原始解析树、运行态时间序列或 UI 专有字段。</p>
 */
public record AtlasGraph(List<ArchitectureAsset> nodes, List<AtlasRelation> relations) {

    public AtlasGraph {
        nodes = sortAndValidateNodes(nodes);
        relations = sortAndValidateRelations(relations, nodes);
    }

    public static AtlasGraph empty() {
        return new AtlasGraph(List.of(), List.of());
    }

    private static List<ArchitectureAsset> sortAndValidateNodes(List<ArchitectureAsset> nodes) {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        Map<AtlasId, ArchitectureAsset> byId = new LinkedHashMap<>();
        for (ArchitectureAsset node : nodes) {
            ArchitectureAsset previous = byId.putIfAbsent(Objects.requireNonNull(node, "node 不能为空").id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("Atlas 节点身份重复: " + node.id());
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(ArchitectureAsset::id)).toList();
    }

    private static List<AtlasRelation> sortAndValidateRelations(
            List<AtlasRelation> relations,
            Collection<ArchitectureAsset> nodes) {
        Objects.requireNonNull(relations, "relations 不能为空");
        Map<AtlasId, ArchitectureAsset> nodesById = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(ArchitectureAsset::id, node -> node));
        Map<AtlasId, AtlasRelation> byId = new LinkedHashMap<>();
        for (AtlasRelation relation : relations) {
            relation = Objects.requireNonNull(relation, "relation 不能为空");
            if (!nodesById.containsKey(relation.sourceId()) || !nodesById.containsKey(relation.targetId())) {
                throw new IllegalArgumentException("关系引用了不存在的节点: " + relation.id());
            }
            AtlasRelation previous = byId.putIfAbsent(relation.id(), relation);
            if (previous != null) {
                throw new IllegalArgumentException("Atlas 关系身份重复: " + relation.id());
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(AtlasRelation::id)).toList();
    }
}
