package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionBinding;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionMatcherKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ConventionProfile;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 读取 JSON Convention Profile；Profile 是可发布约定，Workspace 只保存其私有位置。 */
public final class ConventionProfileJsonReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public ConventionProfile read(Path profileFile) {
        Path file = Objects.requireNonNull(profileFile, "profileFile 不能为空").toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Convention Profile 不存在: " + file);
        }
        try {
            JsonNode root = mapper.readTree(file.toFile());
            if (root == null || !root.isObject() || !"1.0".equals(text(root, "schemaVersion"))) {
                throw new IllegalArgumentException("Convention Profile 必须是 schemaVersion 1.0 的 JSON 对象: " + file);
            }
            JsonNode bindings = root.path("bindings");
            if (!bindings.isArray()) {
                throw new IllegalArgumentException("Convention Profile bindings 必须是数组: " + file);
            }
            List<ConventionBinding> parsed = new ArrayList<>();
            for (JsonNode binding : bindings) {
                parsed.add(new ConventionBinding(
                        AtlasId.of(text(binding, "id")),
                        ConventionMatcherKind.valueOf(text(binding, "matcherKind")),
                        text(binding, "matcher"),
                        FactKind.valueOf(text(binding, "emittedFactKind")),
                        values(binding.path("declaredAttributes")),
                        textOrEmpty(binding, "summary")));
            }
            return new ConventionProfile("1.0", AtlasId.of(text(root, "profileId")), parsed);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Convention Profile: " + file, exception);
        }
    }

    static Map<String, AtlasValue> values(JsonNode object) {
        if (!object.isObject()) {
            throw new IllegalArgumentException("属性必须是 JSON 对象");
        }
        Map<String, AtlasValue> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = object.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            values.put(entry.getKey(), value(entry.getValue()));
        }
        return Map.copyOf(values);
    }

    static AtlasValue value(JsonNode node) {
        if (node.isTextual()) {
            return AtlasValue.text(node.asText());
        }
        if (node.isNumber()) {
            return AtlasValue.decimal(node.decimalValue());
        }
        if (node.isBoolean()) {
            return AtlasValue.bool(node.asBoolean());
        }
        if (node.isArray()) {
            List<AtlasValue> values = new ArrayList<>();
            node.forEach(item -> values.add(value(item)));
            return AtlasValue.values(values);
        }
        if (node.isObject()) {
            return AtlasValue.object(values(node));
        }
        throw new IllegalArgumentException("Atlas 属性不允许 null");
    }

    static String text(JsonNode node, String field) {
        String value = textOrEmpty(node, field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("缺少非空文本字段: " + field);
        }
        return value;
    }

    static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }
}
