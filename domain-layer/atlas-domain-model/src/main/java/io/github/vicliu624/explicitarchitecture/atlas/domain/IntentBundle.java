package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 编译期意图 Manifest 读取后的类型化集合。 */
public record IntentBundle(List<IntentDeclaration> declarations) {

    public IntentBundle {
        Objects.requireNonNull(declarations, "declarations 不能为空");
        Map<AtlasId, IntentDeclaration> byId = new LinkedHashMap<>();
        for (IntentDeclaration declaration : declarations) {
            declaration = Objects.requireNonNull(declaration, "意图声明不能为空");
            if (byId.putIfAbsent(declaration.id(), declaration) != null) {
                throw new IllegalArgumentException("意图声明身份重复: " + declaration.id());
            }
        }
        declarations = List.copyOf(byId.values().stream().sorted(Comparator.comparing(IntentDeclaration::id)).toList());
    }

    public static IntentBundle empty() {
        return new IntentBundle(List.of());
    }
}
