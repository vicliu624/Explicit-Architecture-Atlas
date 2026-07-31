package io.github.vicliu624.explicitarchitecture.atlas.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Atlas Package 中可序列化的原生属性值。
 *
 * <p>不用 {@code Map<String, Object>}，以避免分析器、内容包和 Web 对同一个字段出现不同的隐式类型约定。</p>
 */
public sealed interface AtlasValue permits AtlasValue.Text, AtlasValue.Decimal, AtlasValue.Bool,
        AtlasValue.Values, AtlasValue.ObjectValue {

    static Text text(String value) {
        return new Text(value);
    }

    static Decimal decimal(BigDecimal value) {
        return new Decimal(value);
    }

    static Bool bool(boolean value) {
        return new Bool(value);
    }

    static Values values(List<AtlasValue> values) {
        return new Values(values);
    }

    static ObjectValue object(Map<String, AtlasValue> entries) {
        return new ObjectValue(entries);
    }

    record Text(String value) implements AtlasValue {
        public Text {
            Objects.requireNonNull(value, "value 不能为空");
        }
    }

    record Decimal(BigDecimal value) implements AtlasValue {
        public Decimal {
            Objects.requireNonNull(value, "value 不能为空");
        }
    }

    record Bool(boolean value) implements AtlasValue {
    }

    record Values(List<AtlasValue> values) implements AtlasValue {
        public Values {
            values = List.copyOf(Objects.requireNonNull(values, "values 不能为空"));
        }
    }

    record ObjectValue(Map<String, AtlasValue> entries) implements AtlasValue {
        public ObjectValue {
            entries = Map.copyOf(Objects.requireNonNull(entries, "entries 不能为空"));
            if (entries.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
                throw new IllegalArgumentException("属性对象不能包含空键");
            }
        }
    }
}
