package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProcessManager;

/** 跨聚合协作的最小 Process Manager 样例。 */
@AtlasProcessManager(
        id = "sample-order-completion",
        name = "样例订单完成协调",
        responsibility = "在关联事件到达后推进样例订单完成。",
        completionCondition = "所有必要的确认事件已经被关联。")
public final class OrderCompletionProcess {
}
