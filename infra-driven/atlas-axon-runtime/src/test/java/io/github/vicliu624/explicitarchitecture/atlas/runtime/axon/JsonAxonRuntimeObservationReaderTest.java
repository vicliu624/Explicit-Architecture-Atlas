package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHandlingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证离线运行态导入只接受受控结构并在进入 Atlas 前丢弃敏感属性。 */
class JsonAxonRuntimeObservationReaderTest {

    @TempDir
    Path directory;

    @Test
    void readsAWhitelistedObservationAndDropsPayloadAndErrorText() throws IOException {
        Path file = directory.resolve("runtime-observations.json");
        Files.writeString(file, """
                {
                  "schemaVersion": "1.0",
                  "capturedAt": "2026-07-31T08:00:00Z",
                  "observations": [
                    {
                      "id": "obs:summary:failed",
                      "eventType": "example.OrderCreated",
                      "handlerType": "example.OrderSummaryProjection",
                      "observedAt": "2026-07-31T07:59:58Z",
                      "status": "FAILED",
                      "attributes": {
                        "errorCode": "WRITE_TIMEOUT",
                        "retryCount": 2,
                        "payload": "customer=private",
                        "errorMessage": "private stack-like text"
                      }
                    }
                  ]
                }
                """);

        AxonRuntimeObservationBatch batch = new JsonAxonRuntimeObservationReader(List.of(file)).read();
        AxonRuntimeObservation observation = batch.observations().getFirst();

        assertEquals(Instant.parse("2026-07-31T08:00:00Z"), batch.capturedAt());
        assertEquals(RuntimeHandlingStatus.FAILED, observation.status());
        assertEquals("WRITE_TIMEOUT", ((AtlasValue.Text) observation.attributes().get("errorCode")).value());
        assertFalse(observation.attributes().containsKey("payload"));
        assertFalse(observation.attributes().containsKey("errorMessage"));
        assertTrue(observation.attributes().containsKey("discardedAttributeCount"));
        assertEquals("runtime://axon/offline-export-1", observation.evidence().locator().uri());
        assertEquals(1, new AxonRuntimeSignalMapper().map(batch).signals().size());
    }
}
