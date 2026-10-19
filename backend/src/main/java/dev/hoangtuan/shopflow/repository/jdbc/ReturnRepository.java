package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.dto.customerreturn.ReturnItemResponse;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnableItemResponse;
import dev.hoangtuan.shopflow.model.customerreturn.ReturnStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReturnRepository {
  private final JdbcTemplate jdbcTemplate;

  public ReturnRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<ReturnRow> findAll() {
    return jdbcTemplate.query(
        """
            SELECT id, order_id, status, reason, restockable, created_at, updated_at
            FROM shopflow.return_requests
            ORDER BY created_at DESC, id DESC
            """,
        this::returnRow);
  }

  public List<OrderRow> findDeliveredOrders() {
    return jdbcTemplate.query(
        """
            SELECT id, receiver_name, city, total_amount, created_at
            FROM shopflow.orders
            WHERE delivery_status = 'DELIVERED'
            ORDER BY created_at DESC, id DESC
            """,
        (resultSet, rowNumber) ->
            new OrderRow(
                resultSet.getLong("id"),
                resultSet.getString("receiver_name"),
                resultSet.getString("city"),
                resultSet.getBigDecimal("total_amount"),
                instant(resultSet.getTimestamp("created_at"))));
  }

  public ReturnRow findById(Long returnId) {
    return jdbcTemplate.queryForObject(
        """
            SELECT id, order_id, status, reason, restockable, created_at, updated_at
            FROM shopflow.return_requests
            WHERE id = ?
            """,
        this::returnRow,
        returnId);
  }

  public List<ReturnableItemResponse> returnableItems(Long orderId) {
    return jdbcTemplate.query(
        """
        SELECT oi.id AS order_item_id, oi.product_id, oi.product_name, oi.quantity,
               COALESCE(r.returned_quantity, 0) AS returned_quantity
        FROM shopflow.order_items oi
        LEFT JOIN (
            SELECT ri.order_item_id, SUM(ri.quantity) AS returned_quantity
            FROM shopflow.return_request_items ri
            JOIN shopflow.return_requests rr ON rr.id = ri.return_request_id
            JOIN shopflow.order_items rio ON rio.id = ri.order_item_id
            WHERE rio.order_id = ? AND rr.status IN ('REQUESTED', 'APPROVED', 'RESTOCKED')
            GROUP BY ri.order_item_id
        ) r ON r.order_item_id = oi.id
        WHERE oi.order_id = ?
        ORDER BY oi.id
        """,
        (resultSet, rowNumber) -> {
          int quantity = resultSet.getInt("quantity");
          int returnedQuantity = resultSet.getInt("returned_quantity");
          return new ReturnableItemResponse(
              resultSet.getLong("order_item_id"),
              resultSet.getLong("product_id"),
              resultSet.getString("product_name"),
              quantity,
              returnedQuantity,
              quantity - returnedQuantity);
        },
        orderId,
        orderId);
  }

  public List<ReturnItemResponse> items(Long returnId) {
    return jdbcTemplate.query(
        """
        SELECT ri.order_item_id, oi.product_id, oi.product_name, ri.quantity
        FROM shopflow.return_request_items ri
        JOIN shopflow.order_items oi ON oi.id = ri.order_item_id
        WHERE ri.return_request_id = ?
        ORDER BY ri.id
        """,
        (resultSet, rowNumber) ->
            new ReturnItemResponse(
                resultSet.getLong("order_item_id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getInt("quantity")),
        returnId);
  }

  public OrderStateRow findOrderForUpdate(Long orderId) {
    List<OrderStateRow> orders =
        jdbcTemplate.query(
            "SELECT id, delivery_status FROM shopflow.orders WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) ->
                new OrderStateRow(resultSet.getLong("id"), resultSet.getString("delivery_status")),
            orderId);
    return orders.isEmpty() ? null : orders.getFirst();
  }

  public ReturnRow findReturnForUpdate(Long returnId) {
    List<ReturnRow> returns =
        jdbcTemplate.query(
            """
            SELECT id, order_id, status, reason, restockable, created_at, updated_at
            FROM shopflow.return_requests
            WHERE id = ?
            FOR UPDATE
            """,
            this::returnRow,
            returnId);
    return returns.isEmpty() ? null : returns.getFirst();
  }

  public void lockProduct(Long productId) {
    jdbcTemplate.query(
        "SELECT id FROM shopflow.products WHERE id = ? FOR UPDATE",
        (resultSet, rowNumber) -> resultSet.getLong("id"),
        productId);
  }

  public Integer findOnHandStockForUpdate(Long productId) {
    List<Integer> onHandStock =
        jdbcTemplate.query(
            """
            SELECT on_hand_stock
            FROM shopflow.inventory_items
            WHERE product_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) -> resultSet.getInt("on_hand_stock"),
            productId);
    return onHandStock.isEmpty() ? null : onHandStock.getFirst();
  }

  public Long insertReturn(Long orderId, String reason) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO shopflow.return_requests (order_id, reason)
                  VALUES (?, ?)
                  """,
                  new String[] {"id"});
          statement.setLong(1, orderId);
          statement.setString(2, reason);
          return statement;
        },
        keyHolder);
    Number id = keyHolder.getKey();
    if (id == null) {
      throw new IllegalStateException("Return request was not created");
    }
    return id.longValue();
  }

  public void updateStatus(
      Long returnId, ReturnStatus fromStatus, ReturnStatus toStatus, boolean restockable) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE shopflow.return_requests
            SET status = ?, restockable = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = ?
            """,
            toStatus.name(),
            restockable,
            returnId,
            fromStatus.name());
    if (updated != 1) {
      throw new IllegalStateException("Return transition was not applied");
    }
  }

  private ReturnRow returnRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ReturnRow(
        resultSet.getLong("id"),
        resultSet.getLong("order_id"),
        ReturnStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("reason"),
        resultSet.getBoolean("restockable"),
        instant(resultSet.getTimestamp("created_at")),
        instant(resultSet.getTimestamp("updated_at")));
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp.toInstant();
  }

  public record OrderStateRow(Long id, String deliveryStatus) {}

  public record OrderRow(
      Long id, String receiverName, String city, BigDecimal totalAmount, Instant createdAt) {}

  public record ReturnRow(
      Long id,
      Long orderId,
      ReturnStatus status,
      String reason,
      boolean restockable,
      Instant createdAt,
      Instant updatedAt) {}

  public void insertItem(Long returnId, Long orderItemId, int quantity) {
    jdbcTemplate.update(
        """
          INSERT INTO shopflow.return_request_items (return_request_id, order_item_id, quantity)
          VALUES (?, ?, ?)
          """,
        returnId,
        orderItemId,
        quantity);
  }

  public void createInventory(Long productId) {
    jdbcTemplate.update("INSERT INTO shopflow.inventory_items (product_id) VALUES (?)", productId);
  }

  public void updateStock(Long productId, int onHandStock) {
    jdbcTemplate.update(
        """
        UPDATE shopflow.inventory_items
        SET on_hand_stock = ?, updated_at = CURRENT_TIMESTAMP
        WHERE product_id = ?
        """,
        onHandStock,
        productId);
  }

  public void recordRestock(Long productId, int quantity, Long returnId) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements
            (product_id, type, quantity, reference_type, reference_id)
        VALUES (?, 'RETURN_RESTOCK', ?, 'RETURN', ?)
        """,
        productId,
        quantity,
        returnId);
  }
}
