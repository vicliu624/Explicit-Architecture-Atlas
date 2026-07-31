package io.github.vicliu624.explicitarchitecture.atlas.integration;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasBuildRequest;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshot;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasSnapshotRepository;
import io.github.vicliu624.explicitarchitecture.atlas.application.core.DefaultAtlasBuildService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证应用层不依赖 Web、Spring、Axon 或文件系统即可完成基本编排。 */
class AtlasBuildFlowTest {

    @Test
    void buildCreatesAndPersistsAnEmptySnapshotBeforeAnalyzersAreInstalled() {
        InMemoryRepository repository = new InMemoryRepository();
        AtlasSnapshot snapshot = new DefaultAtlasBuildService(repository).build(new AtlasBuildRequest("sample"));

        assertEquals("sample-empty", snapshot.id());
        assertTrue(repository.findById(snapshot.id()).isPresent());
    }

    private static final class InMemoryRepository implements AtlasSnapshotRepository {

        private final Map<String, AtlasSnapshot> data = new HashMap<>();

        @Override
        public void save(AtlasSnapshot snapshot) {
            data.put(snapshot.id(), snapshot);
        }

        @Override
        public Optional<AtlasSnapshot> findById(String snapshotId) {
            return Optional.ofNullable(data.get(snapshotId));
        }
    }
}
