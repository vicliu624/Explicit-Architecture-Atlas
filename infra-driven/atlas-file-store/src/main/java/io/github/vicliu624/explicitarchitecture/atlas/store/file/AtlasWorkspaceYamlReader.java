package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.AtlasWorkspace;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceContent;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceProject;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceRuntime;
import io.github.vicliu624.explicitarchitecture.atlas.application.api.WorkspaceSources;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                        pathList(configurationDirectory, sources.path("sourceArchives"), normalizedFile, "sourceArchives"),
                        pathList(configurationDirectory, sources.path("intentManifests"), normalizedFile, "intentManifests")),
                optionalObject(root, "content").map(content -> new WorkspaceContent(
                        resolvePath(configurationDirectory, requiredText(content, "root", normalizedFile)))),
                optionalObject(root, "runtime").map(runtime -> new WorkspaceRuntime(runtime.path("enabled").asBoolean(false))),
                optionalText(root, "conventionProfileFile").map(value -> resolvePath(configurationDirectory, value)));
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

    private static Path resolvePath(Path configurationDirectory, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : configurationDirectory.resolve(path)).toAbsolutePath().normalize();
    }
}
