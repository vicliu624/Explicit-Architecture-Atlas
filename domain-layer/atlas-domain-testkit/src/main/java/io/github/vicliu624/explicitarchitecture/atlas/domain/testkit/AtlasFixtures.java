package io.github.vicliu624.explicitarchitecture.atlas.domain.testkit;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import java.util.List;

/** 为 Atlas 扩展者提供的最小稳定测试构造器。 */
public final class AtlasFixtures {
    private AtlasFixtures() {
    }

    public static ArchitectureAsset businessConcept(String id, String name) {
        AtlasId atlasId = AtlasId.of(id);
        return new BusinessConcept(
                AssetMetadata.of(atlasId, name, List.of(new Evidence(
                        AtlasId.of("evidence:" + id),
                        EvidenceOrigin.ANNOTATION_MANIFEST,
                        EvidenceClaim.DECLARED,
                        EvidenceStatus.VERIFIED,
                        new SourceLocator("generated://fixture/" + id, name, SourceRange.unknown()),
                        "fixture",
                        EvidenceVisibility.EXPERT_ONLY,
                        "fixture",
                        "测试资产"))),
                "测试业务概念");
    }
}
