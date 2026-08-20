package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 可复现的 Profile、属性、Classpath 和覆盖集合；用于将可能图解析为场景图。 */
public record ConfigurationScenario(
        AssetMetadata metadata,
        List<String> activeProfiles,
        Map<String, ScenarioProperty> properties,
        List<String> classpathCapabilities,
        ClasspathCapabilityCompleteness classpathCapabilityCompleteness,
        ApplicationContextScope applicationContext) implements ArchitectureAsset {

    public ConfigurationScenario {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        activeProfiles = normalize(activeProfiles, "activeProfiles");
        Objects.requireNonNull(properties, "properties 不能为空");
        Map<String, ScenarioProperty> sorted = new TreeMap<>();
        properties.forEach((key, value) -> {
            String normalized = text(key, "propertyKey");
            if (sorted.putIfAbsent(normalized, Objects.requireNonNull(value, "property 不能为空")) != null) {
                throw new IllegalArgumentException("场景配置键重复: " + normalized);
            }
        });
        properties = Map.copyOf(sorted);
        classpathCapabilities = normalize(classpathCapabilities, "classpathCapabilities");
        classpathCapabilityCompleteness = Objects.requireNonNull(classpathCapabilityCompleteness, "classpathCapabilityCompleteness 不能为空");
        applicationContext = Objects.requireNonNull(applicationContext, "applicationContext 不能为空");
    }

    @Override
    public AssetKind kind() {
        return AssetKind.CONFIGURATION_SCENARIO;
    }

    /** 保持已有 Package/扩展调用的构造方式；它代表无边界的历史可能图，不能被当成真实启动上下文。 */
    public ConfigurationScenario(
            AssetMetadata metadata,
            List<String> activeProfiles,
            Map<String, ScenarioProperty> properties,
            List<String> classpathCapabilities) {
        this(metadata, activeProfiles, properties, classpathCapabilities, ClasspathCapabilityCompleteness.PARTIAL,
                ApplicationContextScope.global());
    }

    /** 允许调用方只补充应用上下文；classpath 缺失项仍必须保持未决。 */
    public ConfigurationScenario(
            AssetMetadata metadata,
            List<String> activeProfiles,
            Map<String, ScenarioProperty> properties,
            List<String> classpathCapabilities,
            ApplicationContextScope applicationContext) {
        this(metadata, activeProfiles, properties, classpathCapabilities, ClasspathCapabilityCompleteness.PARTIAL, applicationContext);
    }

    private static List<String> normalize(List<String> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空").stream()
                .map(value -> text(value, field)).distinct().sorted(Comparator.naturalOrder()).toList());
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
