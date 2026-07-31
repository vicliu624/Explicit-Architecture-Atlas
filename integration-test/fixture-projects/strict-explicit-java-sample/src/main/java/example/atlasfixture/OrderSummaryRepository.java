package example.atlasfixture;

import org.springframework.data.jpa.repository.JpaRepository;

/** 查询侧订单摘要的显式持久化入口。 */
public interface OrderSummaryRepository extends JpaRepository<OrderSummaryEntity, String> {
}
