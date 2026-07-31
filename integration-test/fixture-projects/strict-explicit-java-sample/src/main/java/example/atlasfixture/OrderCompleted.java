package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEvent;

/** 表示样例订单协调流程已经完成。 */
@AtlasEvent(
        id = "sample-order-completed",
        name = "样例订单已完成",
        fact = "样例订单已经完成后续协调。",
        payload = {"orderId"})
public record OrderCompleted(String orderId) {
}
