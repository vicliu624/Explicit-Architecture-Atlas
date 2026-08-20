package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasWorkspace;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceContent;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceProject;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceRuntime;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceScenario;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BeanDefinitionOverridePolicy;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ClasspathCapabilityCompleteness;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceSources;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 将私有 YAML Workspace 转为应用层类型；此处是唯一允许接触本机绝对路径的入口之一。 */
public final class AtlasWorkspaceYamlReader {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public AtlasWorkspace read(Path workspaceFile) {
        Path normalizedFile = Objects.requireNonNull(workspaceFile, "workspaceFile 不能为空").toAbsolutePath().normalize();
        JsonNode root = readTree(normalizedFile);
        requireSchemaVersion(root, normalizedFile);
        Path configurationDirectory = normalizedFile.getParent();
        JsonNode project = requiredObject(root, "project", normalizedFile);
        JsonNode sources = requiredObject(root, "sources", normalizedFile);
        return new AtlasWorkspace(
                requiredText(root, "schemaVersion", normalizedFile),
                AtlasId.of(requiredText(root, "workspaceId", normalizedFile)),
                new WorkspaceProject(
                        AtlasId.of(requiredText(project, "id", normalizedFile)),
                        requiredText(project, "displayName", normalizedFile)),
                new WorkspaceSources(
                        resolvePath(configurationDirectory, requiredText(sources, "projectRoot", normalizedFile)),
                        pathList(configurationDirectory, sources.path("sourceRoots"), normalizedFile, "sourceRoots"),
                        pathList(configurationDirectory, sources.path("excludedPaths"), normalizedFile, "excludedPaths"),
                        pathList(configurationDirectory, sources.path("sourceArchives"), normalizedFile, "sourceArchives"),
                        pathList(configurationDirectory, sources.path("intentManifests"), normalizedFile, "intentManifests")),
                optionalObject(root, "content").map(content -> new WorkspaceContent(
                        resolvePath(configurationDirectory, requiredText(content, "root", normalizedFile)))),
                optionalObject(root, "runtime").map(runtime -> new WorkspaceRuntime(
                        runtime.path("enabled").asBoolean(false),
                        textList(runtime.path("providers"), normalizedFile, "runtime.providers"),
                        pathList(configurationDirectory, runtime.path("observationFiles"), normalizedFile, "runtime.observationFiles"))),
                optionalText(root, "conventionProfileFile").map(value -> resolvePath(configurationDirectory, value)),
                scenarios(root.path("scenarios"), normalizedFile));
    }

    private JsonNode readTree(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Workspace 文件不存在: " + file);
        }
        try {
            JsonNode tree = mapper.readTree(file.toFile());
            if (tree == null || !tree.isObject()) {
                throw new IllegalArgumentException("Workspace 根节点必须是对象: " + file);
            }
            return tree;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Workspace YAML: " + file, exception);
        }
    }

    private static void requireSchemaVersion(JsonNode root, Path file) {
        if (!"1.0".equals(requiredText(root, "schemaVersion", file))) {
            throw new IllegalArgumentException("不支持的 Workspace schemaVersion（仅支持 1.0）: " + file);
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String field, Path file) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new IllegalArgumentException("Workspace 必须包含对象字段 " + field + ": " + file);
        }
        return value;
    }

    private static Optional<JsonNode> optionalObject(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        return value.isObject() ? Optional.of(value) : Optional.empty();
    }

    private static String requiredText(JsonNode parent, String field, Path file) {
        return optionalText(parent, field)
                .orElseThrow(() -> new IllegalArgumentException("Workspace 必须包含非空文本字段 " + field + ": " + file));
    }

    private static Optional<String> optionalText(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.asText().trim());
    }

    private static List<Path> pathList(Path root, JsonNode node, Path file, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Workspace 字段 " + field + " 必须是数组: " + file);
        }
        java.util.ArrayList<Path> paths = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException("Workspace 数组 " + field + " 只能包含非空路径: " + file);
            }
            paths.add(resolvePath(root, item.asText()));
        }
        return List.copyOf(paths);
    }

    private static List<String> textList(JsonNode node, Path file, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Workspace 字段 " + field + " 必须是数组: " + file);
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException("Workspace 数组 " + field + " 只能包含非空文本: " + file);
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private static List<WorkspaceScenario> scenarios(JsonNode node, Path file) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Workspace 字段 scenarios 必须是数组: " + file);
        }
        java.util.ArrayList<WorkspaceScenario> scenarios = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isObject()) {
                throw new IllegalArgumentException("Workspace scenarios 只能包含对象: " + file);
            }
            scenarios.add(new WorkspaceScenario(
                    AtlasId.of(requiredText(value, "id", file)),
                    requiredText(value, "name", file),
                    textList(value.path("activeProfiles"), file, "scenarios.activeProfiles"),
                    scenarioProperties(value.path("properties"), file),
                    textList(value.path("classpathCapabilities"), file, "scenarios.classpathCapabilities"),
                    classpathCompleteness(value.path("classpathCompleteness"), file),
                    applicationContext(value.path("applicationContext"), file)));
        }
        return List.copyOf(scenarios);
    }

    private static ClasspathCapabilityCompleteness classpathCompleteness(JsonNode node, Path file) {
        if (node.isMissingNode() || node.isNull()) {
            return ClasspathCapabilityCompleteness.PARTIAL;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Workspace scenarios.classpathCompleteness 必须为非空文本: " + file);
        }
        try {
            return ClasspathCapabilityCompleteness.valueOf(node.asText().trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("未知 scenarios.classpathCompleteness: " + node.asText(), invalid);
        }
    }

    /**
     * 读取一个启动上下文边界。缺省配置仍可读取历史 Workspace，但明确降级为 global/unbounded，而非悄悄假设所有
     * 源码属于同一个可执行应用。
     */
    private static ApplicationContextScope applicationContext(JsonNode node, Path file) {
        if (node.isMissingNode() || node.isNull()) {
            return ApplicationContextScope.global();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Workspace scenarios.applicationContext 必须是对象: " + file);
        }
        JsonNode overridesNode = node.path("explicitBeanOverrides");
        if (!overridesNode.isMissingNode() && !overridesNode.isObject()) {
            throw new IllegalArgumentException("Workspace applicationContext.explicitBeanOverrides 必须是对象: " + file);
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        if (overridesNode.isObject()) {
            overridesNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                    throw new IllegalArgumentException("Workspace 显式 Bean 覆盖必须为非空文本: " + entry.getKey());
                }
                overrides.put(entry.getKey(), entry.getValue().asText().trim());
            });
        }
        String policy = node.path("beanDefinitionOverridePolicy").isMissingNode()
                ? BeanDefinitionOverridePolicy.UNSPECIFIED.name()
                : requiredText(node, "beanDefinitionOverridePolicy", file);
        try {
            return new ApplicationContextScope(
                    requiredText(node, "id", file),
                    textList(node.path("entryPointTypes"), file, "applicationContext.entryPointTypes"),
                    textList(node.path("rootModuleCoordinates"), file, "applicationContext.rootModuleCoordinates"),
                    textList(node.path("sourcePathPrefixes"), file, "applicationContext.sourcePathPrefixes"),
                    textList(node.path("componentScanPackages"), file, "applicationContext.componentScanPackages"),
                    BeanDefinitionOverridePolicy.valueOf(policy),
                    overrides);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Workspace applicationContext 无效: " + invalid.getMessage(), invalid);
        }
    }

    private static Map<String, ScenarioProperty> scenarioProperties(JsonNode node, Path file) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Workspace scenarios.properties 必须是对象: " + file);
        }
        Map<String, ScenarioProperty> properties = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isObject()) {
                throw new IllegalArgumentException("Workspace 场景属性必须是对象: " + entry.getKey());
            }
            JsonNode redacted = value.path("redacted");
            if (!redacted.isBoolean()) {
                throw new IllegalArgumentException("Workspace 场景属性必须显式声明 redacted: " + entry.getKey());
            }
            boolean isRedacted = redacted.asBoolean();
            if (!isRedacted && (!value.path("value").isTextual() || value.path("value").asText().isBlank())) {
                throw new IllegalArgumentException("非脱敏场景属性必须声明非空 value: " + entry.getKey());
            }
            properties.put(entry.getKey(), new ScenarioProperty(
                    requiredText(value, "provenance", file),
                    value.path("value").isTextual() ? value.path("value").asText() : "",
                    isRedacted));
        });
        return Map.copyOf(properties);
    }

    private static Path resolvePath(Path configurationDirectory, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : configurationDirectory.resolve(path)).toAbsolutePath().normalize();
    }
}
