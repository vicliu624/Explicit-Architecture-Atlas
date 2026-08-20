package io.github.vicliu624.explicitarchitecture.atlas.application.api;

import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationContextScope;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ClasspathCapabilityCompleteness;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ScenarioProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Workspace 中可复现、可安全导出的配置场景声明。
 *
 * <p>它不保存 Workspace 文件路径；每个属性只保存来源标签。敏感属性必须使用 {@link ScenarioProperty#redacted()}，
 * 领域对象会在构造时删除其值。</p>
 */
public record WorkspaceScenario(
        AtlasId scenarioId,
        String name,
        List<String> activeProfiles,
        Map<String, ScenarioProperty> properties,
        List<String> classpathCapabilities,
        ClasspathCapabilityCompleteness classpathCapabilityCompleteness,
        ApplicationContextScope applicationContext) {

    public WorkspaceScenario {
        Objects.requireNonNull(scenarioId, "scenarioId 不能为空");
        name = required(name, "name");
        activeProfiles = strings(activeProfiles, "activeProfiles");
        Objects.requireNonNull(properties, "properties 不能为空");
        Map<String, ScenarioProperty> normalized = new TreeMap<>();
        properties.forEach((key, value) -> {
            String propertyKey = required(key, "propertyKey");
            if (normalized.putIfAbsent(propertyKey, Objects.requireNonNull(value, "property 不能为空")) != null) {
                throw new IllegalArgumentException("场景属性重复: " + propertyKey);
            }
        });
        properties = Map.copyOf(normalized);
        classpathCapabilities = strings(classpathCapabilities, "classpathCapabilities");
        classpathCapabilityCompleteness = Objects.requireNonNull(classpathCapabilityCompleteness, "classpathCapabilityCompleteness 不能为空");
        applicationContext = Objects.requireNonNull(applicationContext, "applicationContext 不能为空");
    }

    /** 兼容没有声明上下文边界的旧 Workspace；会显式落入 global/unbounded 语义。 */
    public WorkspaceScenario(
            AtlasId scenarioId,
            String name,
            List<String> activeProfiles,
            Map<String, ScenarioProperty> properties,
            List<String> classpathCapabilities) {
        this(scenarioId, name, activeProfiles, properties, classpathCapabilities, ClasspathCapabilityCompleteness.PARTIAL,
                ApplicationContextScope.global());
    }

    public WorkspaceScenario(
            AtlasId scenarioId,
            String name,
            List<String> activeProfiles,
            Map<String, ScenarioProperty> properties,
            List<String> classpathCapabilities,
            ApplicationContextScope applicationContext) {
        this(scenarioId, name, activeProfiles, properties, classpathCapabilities, ClasspathCapabilityCompleteness.PARTIAL,
                applicationContext);
    }

    private static List<String> strings(List<String> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空").stream()
                .map(value -> required(value, field)).distinct().sorted(Comparator.naturalOrder()).toList());
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
