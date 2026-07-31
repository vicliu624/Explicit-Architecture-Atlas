package example.atlasfixture;

/** 创建样例订单的命令。 */
public record CreateOrderCommand(String orderId, String createdAt) {
}
