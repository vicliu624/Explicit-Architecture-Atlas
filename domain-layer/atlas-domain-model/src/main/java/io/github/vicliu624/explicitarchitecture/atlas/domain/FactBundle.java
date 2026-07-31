package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 所有启用分析器输出的可审计技术事实集合。 */
public record FactBundle(List<SourceFact> facts, List<FactRelation> relations) {

    public FactBundle {
        facts = sortAndValidateFacts(facts);
        relations = sortAndValidateRelations(relations, facts);
    }

    public static FactBundle empty() {
        return new FactBundle(List.of(), List.of());
    }

    private static List<SourceFact> sortAndValidateFacts(List<SourceFact> facts) {
        Objects.requireNonNull(facts, "facts 不能为空");
        Map<AtlasId, SourceFact> byId = new LinkedHashMap<>();
        for (SourceFact fact : facts) {
            fact = Objects.requireNonNull(fact, "fact 不能为空");
            if (byId.putIfAbsent(fact.id(), fact) != null) {
                throw new IllegalArgumentException("技术事实身份重复: " + fact.id());
            }
        }
        return List.copyOf(byId.values().stream().sorted(Comparator.comparing(SourceFact::id)).toList());
    }

    private static List<FactRelation> sortAndValidateRelations(List<FactRelation> relations, List<SourceFact> facts) {
        Objects.requireNonNull(relations, "relations 不能为空");
        Map<AtlasId, SourceFact> factsById = new LinkedHashMap<>();
        for (SourceFact fact : facts) {
            factsById.put(fact.id(), fact);
        }
        Map<AtlasId, FactRelation> byId = new LinkedHashMap<>();
        for (FactRelation relation : relations) {
            relation = Objects.requireNonNull(relation, "事实关系不能为空");
            if (!factsById.containsKey(relation.sourceFactId()) || !factsById.containsKey(relation.targetFactId())) {
                throw new IllegalArgumentException("事实关系引用了不存在的事实: " + relation.id());
            }
            if (byId.putIfAbsent(relation.id(), relation) != null) {
                throw new IllegalArgumentException("事实关系身份重复: " + relation.id());
            }
        }
        return List.copyOf(byId.values().stream().sorted(Comparator.comparing(FactRelation::id)).toList());
    }
}
