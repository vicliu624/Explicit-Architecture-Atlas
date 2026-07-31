package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasAggregate;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasContext;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.ConstraintKind;

/** 一个不含业务领域专名、专门用于验证 Atlas 的严格显式架构样例。 */
@AtlasContext(
        id = "sample-order-context",
        name = "样例订单上下文",
        description = "用于验证领域意图提取、展示和约束渲染的独立样例。")
@AtlasAggregate(
        id = "sample-order",
        name = "样例订单",
        responsibility = "维护订单身份和创建前必须满足的基本规则。")
public final class OrderContext {

    private OrderContext() {
    }

    @AtlasConstraint(
            id = "sample-order-id-format",
            name = "订单身份格式",
            kind = ConstraintKind.IDENTITY_FORMAT,
            expression = "^[A-Z]{2}-\\d{6}$",
            businessMeaning = "订单编号由两位业务前缀和六位流水号组成，防止错误身份进入事件流。",
            enforcedBy = "OrderId.of")
    public static String orderId(String value) {
        if (value == null || !value.matches("^[A-Z]{2}-\\d{6}$")) {
            throw new IllegalArgumentException("订单编号格式不正确");
        }
        return value;
    }
}
