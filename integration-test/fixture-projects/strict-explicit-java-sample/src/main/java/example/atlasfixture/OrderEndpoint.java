package example.atlasfixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasEndpoint;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 真实 Spring MVC 入口与显式业务目的的最小组合样例。 */
@RestController
@RequestMapping("/orders")
public final class OrderEndpoint {

    private final CommandGateway commandGateway;

    public OrderEndpoint(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @PostMapping(path = "/create")
    @AtlasEndpoint(
            id = "sample-order-create",
            name = "创建样例订单",
            purpose = "接收外部创建请求，并进入订单创建调用链。")
    public Map<String, String> create(@RequestBody OrderCreateRequest request) {
        commandGateway.sendAndWait(new CreateOrderCommand(OrderContext.orderId(request.orderId()), request.createdAt()));
        return Map.of("accepted", "true");
    }
}
