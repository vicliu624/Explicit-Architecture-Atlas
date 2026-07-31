package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEndpoint;

/** 模拟框架接口之外的显式业务入口声明。 */
public final class OrderEndpoint {

    @AtlasEndpoint(
            id = "sample-order-create",
            name = "创建样例订单",
            purpose = "接收外部创建请求，并进入订单创建调用链。")
    public String create(String orderId) {
        return OrderContext.orderId(orderId);
    }
}
