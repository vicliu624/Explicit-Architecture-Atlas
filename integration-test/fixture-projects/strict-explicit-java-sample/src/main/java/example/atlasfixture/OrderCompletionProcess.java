package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProcessManager;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;

/** 跨聚合协作的真实 Axon Saga 最小样例。 */
@Saga
@AtlasProcessManager(
        id = "sample-order-completion",
        name = "样例订单完成协调",
        responsibility = "在关联事件到达后推进样例订单完成。",
        completionCondition = "所有必要的确认事件已经被关联。")
public final class OrderCompletionProcess {

    private transient CommandGateway commandGateway;

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreated event) {
        commandGateway.send(new CompleteOrderCommand(event.orderId()));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCompleted event) {
        // 订单完成是本样例流程的结束条件。
    }
}
