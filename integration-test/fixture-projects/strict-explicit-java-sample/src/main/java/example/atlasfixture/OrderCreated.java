package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEvent;

/** 表示样例订单已经创建这一不可变业务事实。 */
@AtlasEvent(
        id = "sample-order-created",
        name = "样例订单已创建",
        fact = "一个满足身份格式要求的样例订单已经建立。",
        payload = {"orderId", "createdAt"})
public record OrderCreated(String orderId, String createdAt) {
}
