package io.github.vicliu624.explicitarchitecture.atlas.runtime.axon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RuntimeHandlingStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 读取受控的离线 Axon 观测 JSON 文件。
 *
 * <p>它不接触 Event Store、Saga Store 或日志平台。每个输入文件以固定 Schema 描述一次已脱敏导出；仅允许少量
 * 可运维的属性进入 Atlas，任意 payload、消息正文、账号信息或完整异常文本都会被丢弃。</p>
 */
public final class JsonAxonRuntimeObservationReader implements AxonRuntimeObservationReader {

    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "capturedAt", "observations");
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "id", "eventType", "handlerType", "observedAt", "status", "attributes");
    private static final Set<String> SAFE_ATTRIBUTE_KEYS = Set.of(
            "lagSeconds", "retryCount", "errorCode", "errorCategory", "processingGroup", "eventSequence", "eventId", "messageId");

    private final List<Path> files;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonAxonRuntimeObservationReader(List<Path> files) {
        this.files = List.copyOf(Objects.requireNonNull(files, "files 不能为空").stream()
                .map(file -> Objects.requireNonNull(file, "files 不能包含 null").toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .toList());
        if (this.files.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个 Axon 运行态观测文件");
        }
    }

    @Override
    public AxonRuntimeObservationBatch read() {
        List<AxonRuntimeObservation> observations = new ArrayList<>();
        Instant capturedAt = Instant.MIN;
        for (int index = 0; index < files.size(); index++) {
            ParsedFile parsed = readFile(files.get(index), index + 1);
            capturedAt = capturedAt.isAfter(parsed.capturedAt()) ? capturedAt : parsed.capturedAt();
            observations.addAll(parsed.observations());
        }
        return new AxonRuntimeObservationBatch(capturedAt, observations);
    }

    private ParsedFile readFile(Path file, int sourceNumber) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("运行态观测文件不存在: " + file);
        }
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Axon 运行态观测 JSON: " + file, exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Axon 运行态观测根节点必须是对象: " + file);
        }
        requireOnly(root, ROOT_FIELDS, "运行态导出根节点", file);
        if (!"1.0".equals(text(root, "schemaVersion", file))) {
            throw new IllegalArgumentException("不支持的 Axon 运行态导出 schemaVersion（仅支持 1.0）: " + file);
        }
        Instant capturedAt = instant(root, "capturedAt", file);
        JsonNode array = root.path("observations");
        if (!array.isArray()) {
            throw new IllegalArgumentException("运行态导出 observations 必须是数组: " + file);
        }
        List<AxonRuntimeObservation> observations = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode item = array.get(index);
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("运行态观测项必须是对象: " + file + " #" + index);
            }
            requireOnly(item, OBSERVATION_FIELDS, "运行态观测项", file);
            String observationId = token(item, "id", file);
            observations.add(new AxonRuntimeObservation(
                    observationId,
                    token(item, "eventType", file),
                    token(item, "handlerType", file),
                    instant(item, "observedAt", file),
                    RuntimeHandlingStatus.valueOf(token(item, "status", file)),
                    safeAttributes(item.path("attributes"), file),
                    evidence(sourceNumber, observationId, capturedAt)));
        }
        return new ParsedFile(capturedAt, observations);
    }

    private Map<String, AtlasValue> safeAttributes(JsonNode node, Path file) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("运行态观测 attributes 必须是对象: " + file);
        }
        Map<String, AtlasValue> attributes = new LinkedHashMap<>();
        int discarded = 0;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!SAFE_ATTRIBUTE_KEYS.contains(entry.getKey()) || !isScalar(entry.getValue())) {
                discarded++;
                continue;
            }
            attributes.put(entry.getKey(), scalar(entry.getValue()));
        }
        if (discarded > 0) {
            attributes.put("discardedAttributeCount", AtlasValue.decimal(BigDecimal.valueOf(discarded)));
        }
        return Map.copyOf(attributes);
    }

    private static boolean isScalar(JsonNode node) {
        return node.isTextual() || node.isBoolean() || node.isNumber();
    }

    private static AtlasValue scalar(JsonNode node) {
        if (node.isTextual()) {
            return AtlasValue.text(node.asText());
        }
        if (node.isBoolean()) {
            return AtlasValue.bool(node.booleanValue());
        }
        return AtlasValue.decimal(node.decimalValue());
    }

    private static Evidence evidence(int sourceNumber, String observationId, Instant capturedAt) {
        String identity = sourceNumber + ":" + observationId;
        return new Evidence(
                AtlasId.of("evidence:axon-runtime:" + encode(identity)),
                EvidenceOrigin.RUNTIME_SYSTEM,
                EvidenceClaim.OBSERVED,
                EvidenceStatus.VERIFIED,
                new SourceLocator("runtime://axon/offline-export-" + sourceNumber, observationId, SourceRange.unknown()),
                capturedAt.toString(),
                EvidenceVisibility.OPS_SAFE,
                "axon-runtime-json",
                "来自受控离线 Axon 观测导出的脱敏处理状态");
    }

    private static void requireOnly(JsonNode node, Set<String> allowed, String label, Path file) {
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(label + " 包含不允许字段 " + field + ": " + file);
            }
        }
    }

    private static String text(JsonNode node, String field, Path file) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少非空文本字段 " + field + ": " + file);
        }
        return value.asText().trim();
    }

    private static String token(JsonNode node, String field, Path file) {
        String value = text(node, field, file);
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("字段 " + field + " 必须是无空白稳定文本: " + file);
        }
        return value;
    }

    private static Instant instant(JsonNode node, String field, Path file) {
        try {
            return Instant.parse(text(node, field, file));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("字段 " + field + " 必须是 ISO-8601 时间: " + file, exception);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record ParsedFile(Instant capturedAt, List<AxonRuntimeObservation> observations) {
        private ParsedFile {
            Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
            observations = List.copyOf(Objects.requireNonNull(observations, "observations 不能为空"));
        }
    }
}
