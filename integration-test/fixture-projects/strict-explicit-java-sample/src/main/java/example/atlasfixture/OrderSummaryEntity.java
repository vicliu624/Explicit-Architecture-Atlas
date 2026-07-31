package example.atlasfixture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 订单创建事件写入的查询侧摘要。 */
@Entity(name = "OrderSummary")
@Table(name = "order_summary", schema = "query")
public class OrderSummaryEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    protected OrderSummaryEntity() {
        // JPA 需要无参构造器。
    }

    public OrderSummaryEntity(String orderId, String createdAt) {
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String orderId() {
        return orderId;
    }

    public String createdAt() {
        return createdAt;
    }
}
