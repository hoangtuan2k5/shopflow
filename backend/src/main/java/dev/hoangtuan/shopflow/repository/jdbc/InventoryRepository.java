package dev.hoangtuan.shopflow.repository.jdbc;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
  private final JdbcTemplate jdbcTemplate;

  public InventoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<InventorySnapshot> findAll() {
    return jdbcTemplate.query(
        """
        SELECT p.id AS product_id, p.name AS product_name, p.low_stock_threshold,
               COALESCE(i.on_hand_stock, 0) AS on_hand_stock,
               COALESCE(i.reserved_stock, 0) AS reserved_stock
        FROM shopflow.products p
        LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
        ORDER BY p.id
        """,
        (resultSet, rowNumber) ->
            new InventorySnapshot(
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getInt("on_hand_stock"),
                resultSet.getInt("reserved_stock"),
                resultSet.getObject("low_stock_threshold", Integer.class)));
  }

  public ProductRow findProductForUpdate(Long productId) {
    List<ProductRow> products =
        jdbcTemplate.query(
            "SELECT id, name, low_stock_threshold FROM shopflow.products WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) ->
                new ProductRow(
                    resultSet.getLong("id"),
                    resultSet.getString("name"),
                    resultSet.getObject("low_stock_threshold", Integer.class)),
            productId);
    return products.isEmpty() ? null : products.getFirst();
  }

  public InventoryRow findInventoryForUpdate(Long productId) {
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
                    resultSet.getInt("on_hand_stock"), resultSet.getInt("reserved_stock")),
            productId);
    return inventory.isEmpty() ? null : inventory.getFirst();
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

  public void recordAdjustment(Long productId, int delta, String note) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements (product_id, type, quantity, note)
        VALUES (?, 'MANUAL_ADJUSTMENT', ?, ?)
        """,
        productId,
        delta,
        note);
  }

  public void updateThreshold(Long productId, Integer threshold) {
    jdbcTemplate.update(
        """
        UPDATE shopflow.products
        SET low_stock_threshold = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """,
        threshold,
        productId);
  }

  public record ProductRow(Long id, String name, Integer lowStockThreshold) {}

  public record InventoryRow(int onHandStock, int reservedStock) {}

  public record InventorySnapshot(
      Long productId,
      String productName,
      int onHandStock,
      int reservedStock,
      Integer lowStockThreshold) {}
}
