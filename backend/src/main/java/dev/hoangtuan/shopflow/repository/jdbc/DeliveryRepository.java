package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.dto.delivery.DeliveryHistoryResponse;
import dev.hoangtuan.shopflow.dto.delivery.DeliveryItemResponse;
import dev.hoangtuan.shopflow.model.order.DeliveryStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {
  private final JdbcTemplate jdbcTemplate;

  public DeliveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<OrderRow> findAll() {
    return jdbcTemplate.query(
        """
            SELECT id, status, delivery_status, receiver_name, city, total_amount, created_at
            FROM shopflow.orders
            WHERE status = 'PAID'
            ORDER BY created_at, id
            """,
        (resultSet, rowNumber) ->
            new OrderRow(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                DeliveryStatus.valueOf(resultSet.getString("delivery_status")),
                resultSet.getString("receiver_name"),
                resultSet.getString("city"),
                resultSet.getBigDecimal("total_amount"),
                instant(resultSet.getTimestamp("created_at"))));
  }

  public OrderRow findAndLock(Long orderId) {
    List<OrderRow> orders =
        jdbcTemplate.query(
            """
            SELECT id, status, delivery_status, receiver_name, city, total_amount, created_at
            FROM shopflow.orders
            WHERE id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new OrderRow(
                    resultSet.getLong("id"),
                    resultSet.getString("status"),
                    DeliveryStatus.valueOf(resultSet.getString("delivery_status")),
                    resultSet.getString("receiver_name"),
                    resultSet.getString("city"),
                    resultSet.getBigDecimal("total_amount"),
                    instant(resultSet.getTimestamp("created_at"))),
            orderId);
    return orders.isEmpty() ? null : orders.getFirst();
  }

  public List<DeliveryItemResponse> items(Long orderId) {
    return jdbcTemplate.query(
        """
        SELECT product_id, product_name, SUM(quantity) AS quantity
        FROM shopflow.order_items
        WHERE order_id = ?
        GROUP BY product_id, product_name
        ORDER BY product_id
        """,
        (resultSet, rowNumber) ->
            new DeliveryItemResponse(
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getInt("quantity")),
        orderId);
  }

  public List<DeliveryHistoryResponse> history(Long orderId) {
    return jdbcTemplate.query(
        """
        SELECT from_status, to_status, changed_at, changed_by
        FROM shopflow.delivery_status_history
        WHERE order_id = ?
        ORDER BY changed_at, id
        """,
        (resultSet, rowNumber) -> {
          String fromStatus = resultSet.getString("from_status");
          return new DeliveryHistoryResponse(
              fromStatus == null ? null : DeliveryStatus.valueOf(fromStatus),
              DeliveryStatus.valueOf(resultSet.getString("to_status")),
              instant(resultSet.getTimestamp("changed_at")),
              resultSet.getString("changed_by"));
        },
        orderId);
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp.toInstant();
  }

  public record OrderRow(
      Long orderId,
      String orderStatus,
      DeliveryStatus deliveryStatus,
      String receiverName,
      String city,
      BigDecimal totalAmount,
      Instant createdAt) {}

  public int updateDelivery(Long orderId, DeliveryStatus fromStatus, DeliveryStatus toStatus) {
    return jdbcTemplate.update(
        """
            UPDATE shopflow.orders
            SET delivery_status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND delivery_status = ?
            """,
        toStatus.name(),
        orderId,
        fromStatus.name());
  }

  public void recordHistory(Long orderId, DeliveryStatus fromStatus, DeliveryStatus toStatus) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.delivery_status_history (order_id, from_status, to_status)
        VALUES (?, ?, ?)
        """,
        orderId,
        fromStatus.name(),
        toStatus.name());
  }

  public int completeInventory(DeliveryItemResponse item) {
    return jdbcTemplate.update(
        """
              UPDATE shopflow.inventory_items
              SET on_hand_stock = on_hand_stock - ?,
                  reserved_stock = reserved_stock - ?,
                  updated_at = CURRENT_TIMESTAMP
              WHERE product_id = ? AND on_hand_stock >= ? AND reserved_stock >= ?
              """,
        item.quantity(),
        item.quantity(),
        item.productId(),
        item.quantity(),
        item.quantity());
  }

  public void recordDelivery(Long orderId, DeliveryItemResponse item) {
    jdbcTemplate.update(
        """
          INSERT INTO shopflow.stock_movements
              (product_id, type, quantity, reference_type, reference_id)
          VALUES (?, 'DELIVERY_COMPLETED', ?, 'ORDER', ?)
          """,
        item.productId(),
        -item.quantity(),
        orderId);
  }
}
