package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.model.order.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderRepository {
  private final JdbcTemplate jdbcTemplate;

  public PaymentOrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public OrderPaymentData findAndLockOrder(String orderRef) {
    List<OrderPaymentData> orders =
        jdbcTemplate.query(
            """
            SELECT id, status, payment_method, total_amount
            FROM shopflow.orders
            WHERE order_ref = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new OrderPaymentData(
                    resultSet.getLong("id"),
                    resultSet.getString("status"),
                    PaymentMethod.valueOf(resultSet.getString("payment_method")),
                    resultSet.getBigDecimal("total_amount")),
            orderRef);
    return orders.isEmpty() ? null : orders.getFirst();
  }

  public void updateOrderStatus(Long orderId, String status) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE shopflow.orders
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING_PAYMENT'
            """,
            status,
            orderId);
    if (updated != 1) {
      throw new IllegalStateException("Order status changed while processing payment");
    }
  }

  public record OrderPaymentData(Long id, String status, PaymentMethod method, BigDecimal amount) {}
}
