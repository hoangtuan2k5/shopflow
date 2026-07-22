package dev.hoangtuan.shopflow.delivery;

import dev.hoangtuan.shopflow.order.DeliveryStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryService {

  private final JdbcTemplate jdbcTemplate;

  DeliveryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  List<DeliveryResponse> getDeliveries() {
    return jdbcTemplate
        .query(
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
                    instant(resultSet.getTimestamp("created_at"))))
        .stream()
        // ponytail: MVP-sized queue; batch child reads when delivery volume needs pagination.
        .map(this::response)
        .toList();
  }

  @Transactional
  DeliveryResponse update(Long orderId, UpdateDeliveryRequest request) {
    OrderRow order = findAndLock(orderId);
    if (order == null) {
      throw new DeliveryNotFoundException();
    }
    if (!"PAID".equals(order.orderStatus())) {
      throw new DeliveryConflictException("Order must be PAID before delivery can start");
    }

    DeliveryStatus expected = next(order.deliveryStatus());
    if (request.toStatus() != expected) {
      throw new DeliveryConflictException(
          "Invalid delivery transition: " + order.deliveryStatus() + " -> " + request.toStatus());
    }

    if (request.toStatus() == DeliveryStatus.DELIVERED) {
      completeInventory(orderId);
    }

    int updated =
        jdbcTemplate.update(
            """
            UPDATE shopflow.orders
            SET delivery_status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND delivery_status = ?
            """,
            request.toStatus().name(),
            orderId,
            order.deliveryStatus().name());
    if (updated != 1) {
      throw new IllegalStateException("Delivery status changed while updating order");
    }

    jdbcTemplate.update(
        """
        INSERT INTO shopflow.delivery_status_history (order_id, from_status, to_status)
        VALUES (?, ?, ?)
        """,
        orderId,
        order.deliveryStatus().name(),
        request.toStatus().name());

    return response(
        new OrderRow(
            order.orderId(),
            order.orderStatus(),
            request.toStatus(),
            order.receiverName(),
            order.city(),
            order.totalAmount(),
            order.createdAt()));
  }

  private OrderRow findAndLock(Long orderId) {
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

  private DeliveryStatus next(DeliveryStatus current) {
    return switch (current) {
      case NONE -> DeliveryStatus.PREPARING;
      case PREPARING -> DeliveryStatus.SHIPPED;
      case SHIPPED -> DeliveryStatus.DELIVERED;
      case DELIVERED -> null;
    };
  }

  private void completeInventory(Long orderId) {
    List<DeliveryItemResponse> items = items(orderId);
    for (DeliveryItemResponse item : items) {
      int updated =
          jdbcTemplate.update(
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
      if (updated != 1) {
        throw new DeliveryConflictException("Inventory cannot complete delivery");
      }

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

  private DeliveryResponse response(OrderRow order) {
    return new DeliveryResponse(
        order.orderId(),
        order.orderStatus(),
        order.deliveryStatus(),
        order.receiverName(),
        order.city(),
        order.totalAmount(),
        order.createdAt(),
        items(order.orderId()),
        history(order.orderId()));
  }

  private List<DeliveryItemResponse> items(Long orderId) {
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

  private List<DeliveryHistoryResponse> history(Long orderId) {
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

  private record OrderRow(
      Long orderId,
      String orderStatus,
      DeliveryStatus deliveryStatus,
      String receiverName,
      String city,
      BigDecimal totalAmount,
      Instant createdAt) {}
}
