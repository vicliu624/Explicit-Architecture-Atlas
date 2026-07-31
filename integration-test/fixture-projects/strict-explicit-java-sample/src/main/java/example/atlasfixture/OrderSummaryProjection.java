package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProjection;

/** 用于列表查询的样例读模型。 */
@AtlasProjection(
        id = "sample-order-summary",
        name = "样例订单摘要",
        purpose = "为使用者提供订单已创建状态的快速查询视图。")
public final class OrderSummaryProjection {
}
