package dev.hoangtuan.shopflow.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {

  private final JdbcTemplate jdbcTemplate;

  StockReservationService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public StockValidationResult lockAndValidate(List<StockRequest> requests) {
    validateRequests(requests);

    List<Long> unavailableProductIds = new ArrayList<>();
    List<InsufficientStock> insufficientItems = new ArrayList<>();
    List<ValidatedStockItem> validItems = new ArrayList<>();

    requests.stream()
        .sorted(Comparator.comparing(StockRequest::productId))
        .forEach(
            request -> {
              StockRow row = findAndLock(request.productId());
              if (row == null || !row.active()) {
                unavailableProductIds.add(request.productId());
                return;
              }

              if (row.onHandStock() == null) {
                insufficientItems.add(
                    new InsufficientStock(request.productId(), request.quantity(), 0));
                return;
              }

              int available = row.onHandStock() - row.reservedStock();
              if (available < request.quantity()) {
                insufficientItems.add(
                    new InsufficientStock(request.productId(), request.quantity(), available));
                return;
              }

              validItems.add(
                  new ValidatedStockItem(
                      row.id(), row.name(), row.price(), request.quantity(), available));
            });

    return new StockValidationResult(unavailableProductIds, insufficientItems, validItems);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void reserve(Long orderId, List<ValidatedStockItem> items) {
    if (orderId == null) {
      throw new IllegalArgumentException("orderId is required");
    }

    for (ValidatedStockItem item : items) {
      int updated =
          jdbcTemplate.update(
              """
              UPDATE shopflow.inventory_items
              SET reserved_stock = reserved_stock + ?
              WHERE product_id = ? AND reserved_stock + ? <= on_hand_stock
              """,
              item.quantity(),
              item.productId(),
              item.quantity());
      if (updated != 1) {
        throw new IllegalStateException(
            "Stock changed while reserving product " + item.productId());
      }

      jdbcTemplate.update(
          """
          INSERT INTO shopflow.stock_movements
              (product_id, type, quantity, reference_type, reference_id)
          VALUES (?, 'ORDER_RESERVED', ?, 'ORDER', ?)
          """,
          item.productId(),
          item.quantity(),
          orderId);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void release(Long orderId) {
    List<ReleaseItem> items =
        jdbcTemplate.query(
            """
            SELECT product_id, SUM(quantity) AS quantity
            FROM shopflow.order_items
            WHERE order_id = ?
            GROUP BY product_id
            ORDER BY product_id
            """,
            (resultSet, rowNumber) ->
                new ReleaseItem(resultSet.getLong("product_id"), resultSet.getInt("quantity")),
            orderId);

    for (ReleaseItem item : items) {
      int updated =
          jdbcTemplate.update(
              """
              UPDATE shopflow.inventory_items
              SET reserved_stock = reserved_stock - ?
              WHERE product_id = ? AND reserved_stock >= ?
              """,
              item.quantity(),
              item.productId(),
              item.quantity());
      if (updated != 1) {
        throw new IllegalStateException(
            "Reserved stock is inconsistent for product " + item.productId());
      }

      jdbcTemplate.update(
          """
          INSERT INTO shopflow.stock_movements
              (product_id, type, quantity, reference_type, reference_id)
          VALUES (?, 'PAYMENT_FAILED_RELEASE', ?, 'ORDER', ?)
          """,
          item.productId(),
          -item.quantity(),
          orderId);
    }
  }

  private StockRow findAndLock(Long productId) {
    List<ProductRow> products =
        jdbcTemplate.query(
            """
            SELECT id, name, price, active
            FROM shopflow.products
            WHERE id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new ProductRow(
                    resultSet.getLong("id"),
                    resultSet.getString("name"),
                    resultSet.getBigDecimal("price"),
                    resultSet.getBoolean("active")),
            productId);
    if (products.isEmpty()) {
      return null;
    }

    ProductRow product = products.getFirst();
    List<InventoryRow> inventory =
        jdbcTemplate.query(
            """
            SELECT on_hand_stock, reserved_stock
            FROM shopflow.inventory_items
            WHERE product_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new InventoryRow(
                    (Integer) resultSet.getObject("on_hand_stock"),
                    (Integer) resultSet.getObject("reserved_stock")),
            productId);
    Integer onHandStock = inventory.isEmpty() ? null : inventory.getFirst().onHandStock();
    Integer reservedStock = inventory.isEmpty() ? null : inventory.getFirst().reservedStock();
    return new StockRow(
        product.id(),
        product.name(),
        product.price(),
        product.active(),
        onHandStock,
        reservedStock == null ? 0 : reservedStock);
  }

  private void validateRequests(List<StockRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("at least one stock request is required");
    }

    Set<Long> productIds = new HashSet<>();
    for (StockRequest request : requests) {
      if (request == null || request.productId() == null || request.quantity() <= 0) {
        throw new IllegalArgumentException(
            "stock request must have a product and positive quantity");
      }
      if (!productIds.add(request.productId())) {
        throw new IllegalArgumentException("duplicate product id: " + request.productId());
      }
    }
  }

  private record StockRow(
      Long id,
      String name,
      BigDecimal price,
      boolean active,
      Integer onHandStock,
      Integer reservedStock) {}

  private record ProductRow(Long id, String name, BigDecimal price, boolean active) {}

  private record InventoryRow(Integer onHandStock, Integer reservedStock) {}

  private record ReleaseItem(Long productId, int quantity) {}
}
