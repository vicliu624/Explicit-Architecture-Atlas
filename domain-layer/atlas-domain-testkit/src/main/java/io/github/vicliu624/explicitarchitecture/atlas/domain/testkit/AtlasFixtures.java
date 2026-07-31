package io.github.vicliu624.explicitarchitecture.atlas.domain.testkit;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceSource;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import java.util.List;

/** 为 Atlas 扩展者提供的最小稳定测试构造器。 */
public final class AtlasFixtures {
    private AtlasFixtures() {
    }

    public static ArchitectureAsset businessConcept(String id, String name) {
        return new ArchitectureAsset(
                id,
                AssetKind.BUSINESS_CONCEPT,
                name,
                List.of(new Evidence(EvidenceSource.DECLARED, EvidenceStatus.VERIFIED, "fixture:" + id, "测试资产"))
        );
    }
}
