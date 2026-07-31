package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasAggregate;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/** 使用真实 Axon 注解和事件生命周期的聚合样例。 */
@Aggregate
@AtlasAggregate(
        id = "sample-order-aggregate",
        name = "样例订单聚合",
        responsibility = "处理创建和完成命令，并以不可变事件记录订单事实。")
public final class OrderAggregate {

    @AggregateIdentifier
    private String orderId;

    protected OrderAggregate() {
    }

    @CommandHandler
    public OrderAggregate(CreateOrderCommand command) {
        AggregateLifecycle.apply(new OrderCreated(command.orderId(), command.createdAt()));
    }

    @CommandHandler
    public void handle(CompleteOrderCommand command) {
        AggregateLifecycle.apply(new OrderCompleted(command.orderId()));
    }

    @EventSourcingHandler
    public void on(OrderCreated event) {
        this.orderId = event.orderId();
    }
}
