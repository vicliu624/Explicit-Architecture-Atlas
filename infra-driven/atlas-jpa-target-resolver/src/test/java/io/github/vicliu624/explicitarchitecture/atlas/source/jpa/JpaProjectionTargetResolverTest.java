package io.github.vicliu624.explicitarchitecture.atlas.source.jpa;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 JPA 适配器只输出源码直接证明的持久化事实。 */
class JpaProjectionTargetResolverTest {

    @TempDir
    Path project;

    @Test
    void keepsAnEntityVisibleButNeverGuessesAnImplicitPhysicalTableName() throws IOException {
        Path source = project.resolve("src/main/java/example/UnmappedOrderView.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                @jakarta.persistence.Entity
                final class UnmappedOrderView {
                    @jakarta.persistence.Id
                    private String orderId;
                }
                """);

        AnalyzerContribution contribution = new JpaProjectionTargetResolver().analyze(
                new AnalysisRequest(AtlasId.of("project:jpa-without-table"), project, "revision:test"));

        assertEquals(CapabilityStatus.COMPLETE, contribution.coverage().status());
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.PERSISTENCE_ENTITY));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.PERSISTENCE_COLUMN));
        assertFalse(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.PERSISTENCE_TABLE));
    }
}
