package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一个可启动 Spring（或其他 DI）应用上下文的静态边界。
 *
 * <p>它刻意不是“整个仓库”。同一仓库可以有多个可执行进程，它们拥有不同的 classpath、组件扫描范围、配置与
 * Bean 覆盖规则。{@link ConfigurationScenario} 必须带着此边界解析，才能把“可能的 Bean”收窄为某个进程可见的
 * 候选。路径均为 {@code project://} URI 中的项目相对前缀，不记录机器绝对路径。</p>
 */
public record ApplicationContextScope(
        String contextId,
        List<String> entryPointTypes,
        List<String> rootModuleCoordinates,
        List<String> sourcePathPrefixes,
        List<String> componentScanPackages,
        BeanDefinitionOverridePolicy beanDefinitionOverridePolicy,
        Map<String, String> explicitBeanOverrides) {

    public ApplicationContextScope {
        contextId = required(contextId, "contextId");
        entryPointTypes = values(entryPointTypes, "entryPointTypes", false);
        rootModuleCoordinates = values(rootModuleCoordinates, "rootModuleCoordinates", false);
        sourcePathPrefixes = values(sourcePathPrefixes, "sourcePathPrefixes", true);
        componentScanPackages = values(componentScanPackages, "componentScanPackages", false);
        Objects.requireNonNull(beanDefinitionOverridePolicy, "beanDefinitionOverridePolicy 不能为空");
        Objects.requireNonNull(explicitBeanOverrides, "explicitBeanOverrides 不能为空");
        Map<String, String> overrides = new TreeMap<>();
        explicitBeanOverrides.forEach((beanName, candidateType) -> {
            String name = required(beanName, "explicitBeanOverride.beanName");
            String type = required(candidateType, "explicitBeanOverride.candidateType");
            if (overrides.putIfAbsent(name, type) != null) {
                throw new IllegalArgumentException("显式 Bean 覆盖重复: " + name);
            }
        });
        explicitBeanOverrides = Map.copyOf(overrides);
        if (!explicitBeanOverrides.isEmpty() && beanDefinitionOverridePolicy != BeanDefinitionOverridePolicy.ALLOW) {
            throw new IllegalArgumentException("只有 ALLOW 覆盖策略可以声明 explicitBeanOverrides");
        }
    }

    /** 缺省上下文仅表示历史 Package 的全局可能图，不可被误读为某个已知应用进程。 */
    public static ApplicationContextScope global() {
        return new ApplicationContextScope(
                "context:global:unbounded", List.of(), List.of(), List.of(), List.of(), BeanDefinitionOverridePolicy.UNSPECIFIED, Map.of());
    }

    /** 兼容早期 Workspace；未声明 Maven 根模块时只使用路径/扫描包边界。 */
    public ApplicationContextScope(
            String contextId,
            List<String> entryPointTypes,
            List<String> sourcePathPrefixes,
            List<String> componentScanPackages,
            BeanDefinitionOverridePolicy beanDefinitionOverridePolicy,
            Map<String, String> explicitBeanOverrides) {
        this(contextId, entryPointTypes, List.of(), sourcePathPrefixes, componentScanPackages,
                beanDefinitionOverridePolicy, explicitBeanOverrides);
    }

    private static List<String> values(List<String> values, String field, boolean path) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空").stream()
                .map(value -> required(value, field))
                .map(value -> path ? normalizePathPrefix(value) : value)
                .distinct().sorted(Comparator.naturalOrder()).toList());
    }

    private static String normalizePathPrefix(String value) {
        value = value.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.endsWith("/") ? value : value + "/";
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
