package dev.hoangtuan.shopflow.customerreturn;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReturnService {

  private final JdbcTemplate jdbcTemplate;

  ReturnService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  List<ReturnResponse> list() {
    List<ReturnRow> returns =
        jdbcTemplate.query(
            """
            SELECT id, order_id, status, reason, restockable, created_at, updated_at
            FROM shopflow.return_requests
            ORDER BY created_at DESC, id DESC
            """,
            this::returnRow);
    return returns.stream().map(row -> response(row, items(row.id()))).toList();
  }

  List<ReturnableOrderResponse> returnableOrders() {
    List<OrderRow> orders =
        jdbcTemplate.query(
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
    return orders.stream()
        .map(
            order ->
                new ReturnableOrderResponse(
                    order.id(),
                    order.receiverName(),
                    order.city(),
                    order.totalAmount(),
                    order.createdAt(),
                    returnableItems(order.id())))
        .toList();
  }

  @Transactional
  ReturnResponse create(CreateReturnRequest request) {
    OrderStateRow order = findOrderForUpdate(request.orderId());
    if (order == null) {
      throw new ReturnNotFoundException("Order not found");
    }
    if (!"DELIVERED".equals(order.deliveryStatus())) {
      throw new ReturnConflictException("Order is not delivered");
    }

    String reason = optionalText(request.reason(), "reason", 500);
    Set<Long> seenOrderItemIds = new HashSet<>();
    for (ReturnItemRequest item : request.items()) {
      if (!seenOrderItemIds.add(item.orderItemId())) {
        throw new ReturnValidationException("items", "Must not repeat an order item");
      }
    }

    Map<Long, ReturnableItemResponse> returnable = new LinkedHashMap<>();
    for (ReturnableItemResponse item : returnableItems(request.orderId())) {
      returnable.put(item.orderItemId(), item);
    }
    for (ReturnItemRequest item : request.items()) {
      ReturnableItemResponse target = returnable.get(item.orderItemId());
      if (target == null) {
        throw new ReturnValidationException("items", "Must reference items of the order");
      }
      if (item.quantity() > target.returnableQuantity()) {
        throw new ReturnConflictException("Return exceeds purchased quantity");
      }
    }

    Long returnId = insertReturn(request.orderId(), reason);
    for (ReturnItemRequest item : request.items()) {
      jdbcTemplate.update(
          """
          INSERT INTO shopflow.return_request_items (return_request_id, order_item_id, quantity)
          VALUES (?, ?, ?)
          """,
          returnId,
          item.orderItemId(),
          item.quantity());
    }

    return fetch(returnId);
  }

  @Transactional
  ReturnResponse update(Long returnId, UpdateReturnRequest request) {
    ReturnRow current = findReturnForUpdate(returnId);
    if (current == null) {
      throw new ReturnNotFoundException("Return not found");
    }

    return switch (request.toStatus()) {
      case APPROVED -> approve(current, request.restockable());
      case REJECTED -> reject(current, request.restockable());
      case RESTOCKED -> restock(current, request.restockable());
      case REQUESTED -> throw new ReturnConflictException("Unsupported return transition");
    };
  }

  private ReturnResponse approve(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.REQUESTED);
    if (restockable == null) {
      throw new ReturnValidationException("restockable", "Required when approving");
    }
    updateStatus(current.id(), current.status(), ReturnStatus.APPROVED, restockable);
    return fetch(current.id());
  }

  private ReturnResponse reject(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.REQUESTED);
    requireNoRestockable(restockable);
    updateStatus(current.id(), current.status(), ReturnStatus.REJECTED, false);
    return fetch(current.id());
  }

  private ReturnResponse restock(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.APPROVED);
    requireNoRestockable(restockable);
    if (!current.restockable()) {
      throw new ReturnConflictException("Return is not restockable");
    }

    List<ReturnItemResponse> items =
        items(current.id()).stream()
            .sorted(Comparator.comparing(ReturnItemResponse::productId))
            .toList();
    for (ReturnItemResponse item : items) {
      restockItem(item, current.id());
    }
    updateStatus(current.id(), current.status(), ReturnStatus.RESTOCKED, true);
    return fetch(current.id());
  }

  private void restockItem(ReturnItemResponse item, Long returnId) {
    lockProduct(item.productId());
    Integer onHandStock = findOnHandStockForUpdate(item.productId());
    if (onHandStock == null) {
      jdbcTemplate.update(
          "INSERT INTO shopflow.inventory_items (product_id) VALUES (?)", item.productId());
      onHandStock = 0;
    }

    long newOnHand = (long) onHandStock + item.quantity();
    if (newOnHand > Integer.MAX_VALUE) {
      throw new ReturnConflictException("Restock exceeds inventory limit");
    }

    jdbcTemplate.update(
        """
        UPDATE shopflow.inventory_items
        SET on_hand_stock = ?, updated_at = CURRENT_TIMESTAMP
        WHERE product_id = ?
        """,
        (int) newOnHand,
        item.productId());
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements
            (product_id, type, quantity, reference_type, reference_id)
        VALUES (?, 'RETURN_RESTOCK', ?, 'RETURN', ?)
        """,
        item.productId(),
        item.quantity(),
        returnId);
  }

  private void requireStatus(ReturnRow current, ReturnStatus expected) {
    if (current.status() != expected) {
      throw new ReturnConflictException("Unsupported return transition");
    }
  }

  private void requireNoRestockable(Boolean restockable) {
    if (restockable != null) {
      throw new ReturnValidationException("restockable", "Only applies to approval");
    }
  }

  private void updateStatus(
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

  private List<ReturnableItemResponse> returnableItems(Long orderId) {
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

  private List<ReturnItemResponse> items(Long returnId) {
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

  private OrderStateRow findOrderForUpdate(Long orderId) {
    List<OrderStateRow> orders =
        jdbcTemplate.query(
            "SELECT id, delivery_status FROM shopflow.orders WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) ->
                new OrderStateRow(resultSet.getLong("id"), resultSet.getString("delivery_status")),
            orderId);
    return orders.isEmpty() ? null : orders.getFirst();
  }

  private ReturnRow findReturnForUpdate(Long returnId) {
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

  private void lockProduct(Long productId) {
    jdbcTemplate.query(
        "SELECT id FROM shopflow.products WHERE id = ? FOR UPDATE",
        (resultSet, rowNumber) -> resultSet.getLong("id"),
        productId);
  }

  private Integer findOnHandStockForUpdate(Long productId) {
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

  private Long insertReturn(Long orderId, String reason) {
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

  private ReturnResponse fetch(Long returnId) {
    ReturnRow row =
        jdbcTemplate.queryForObject(
            """
            SELECT id, order_id, status, reason, restockable, created_at, updated_at
            FROM shopflow.return_requests
            WHERE id = ?
            """,
            this::returnRow,
            returnId);
    return response(row, items(returnId));
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

  private ReturnResponse response(ReturnRow row, List<ReturnItemResponse> items) {
    return new ReturnResponse(
        row.id(),
        row.orderId(),
        row.status(),
        row.reason(),
        row.restockable(),
        row.createdAt(),
        row.updatedAt(),
        items);
  }

  private String optionalText(String value, String field, int maximumLength) {
    if (value == null) {
      return null;
    }

    String text = value.strip();
    if (text.isEmpty()) {
      throw new ReturnValidationException(field, "Must not be blank");
    }
    if (text.length() > maximumLength) {
      throw new ReturnValidationException(
          field, "Must be %d characters or fewer".formatted(maximumLength));
    }
    return text;
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp.toInstant();
  }

  private record OrderStateRow(Long id, String deliveryStatus) {}

  private record OrderRow(
      Long id, String receiverName, String city, BigDecimal totalAmount, Instant createdAt) {}

  private record ReturnRow(
      Long id,
      Long orderId,
      ReturnStatus status,
      String reason,
      boolean restockable,
      Instant createdAt,
      Instant updatedAt) {}
}
