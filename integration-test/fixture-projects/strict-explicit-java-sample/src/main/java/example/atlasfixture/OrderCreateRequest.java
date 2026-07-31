package example.atlasfixture;

/** Spring MVC 创建接口的请求体。 */
public record OrderCreateRequest(String orderId, String createdAt) {
}
