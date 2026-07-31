package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不含业务私密数据、可版本化复用的分析约定集合。
 *
 * <p>它用于让 Spring、Axon、JPA 或组织自身的显式架构约定获得一致的事实分类，同时保留注解 Manifest
 * 优先于纯约定推断的原则。</p>
 */
public record ConventionProfile(String schemaVersion, AtlasId profileId, List<ConventionBinding> bindings) {

    public ConventionProfile {
        Objects.requireNonNull(schemaVersion, "schemaVersion 不能为空");
        schemaVersion = schemaVersion.trim();
        if (schemaVersion.isEmpty()) {
            throw new IllegalArgumentException("schemaVersion 不能为空白");
        }
        Objects.requireNonNull(profileId, "profileId 不能为空");
        Objects.requireNonNull(bindings, "bindings 不能为空");
        Map<AtlasId, ConventionBinding> byId = new LinkedHashMap<>();
        for (ConventionBinding binding : bindings) {
            binding = Objects.requireNonNull(binding, "约定绑定不能为空");
            if (byId.putIfAbsent(binding.id(), binding) != null) {
                throw new IllegalArgumentException("约定绑定身份重复: " + binding.id());
            }
        }
        bindings = List.copyOf(byId.values().stream().sorted(Comparator.comparing(ConventionBinding::id)).toList());
    }

    public static ConventionProfile empty() {
        return new ConventionProfile("1.0", AtlasId.of("profile:empty"), List.of());
    }
}
