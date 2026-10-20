package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.model.inventory.ValidatedStockItem;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockReservationRepository {
  private final JdbcTemplate jdbcTemplate;

  public StockReservationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public StockRow findAndLock(Long productId) {
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

  public record StockRow(
      Long id,
      String name,
      BigDecimal price,
      boolean active,
      Integer onHandStock,
      Integer reservedStock) {}

  private record ProductRow(Long id, String name, BigDecimal price, boolean active) {}

  private record InventoryRow(Integer onHandStock, Integer reservedStock) {}

  public record ReleaseItem(Long productId, int quantity) {}

  public int reserveProduct(ValidatedStockItem item) {
    return jdbcTemplate.update(
        """
              UPDATE shopflow.inventory_items
              SET reserved_stock = reserved_stock + ?
              WHERE product_id = ? AND reserved_stock + ? <= on_hand_stock
              """,
        item.quantity(),
        item.productId(),
        item.quantity());
  }

  public void recordReservation(Long orderId, ValidatedStockItem item) {
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

  public List<ReleaseItem> findReleaseItems(Long orderId) {
    return jdbcTemplate.query(
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
  }

  public int releaseProduct(ReleaseItem item) {
    return jdbcTemplate.update(
        """
              UPDATE shopflow.inventory_items
              SET reserved_stock = reserved_stock - ?
              WHERE product_id = ? AND reserved_stock >= ?
              """,
        item.quantity(),
        item.productId(),
        item.quantity());
  }

  public void recordRelease(Long orderId, ReleaseItem item) {
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
