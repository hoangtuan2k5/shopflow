package dev.hoangtuan.shopflow.inventory;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryService {

  private final JdbcTemplate jdbcTemplate;

  InventoryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  List<InventoryResponse> getInventory() {
    return jdbcTemplate.query(
        """
        SELECT p.id AS product_id, p.name AS product_name,
               COALESCE(i.on_hand_stock, 0) AS on_hand_stock,
               COALESCE(i.reserved_stock, 0) AS reserved_stock
        FROM shopflow.products p
        LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
        ORDER BY p.id
        """,
        (resultSet, rowNumber) ->
            response(
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getInt("on_hand_stock"),
                resultSet.getInt("reserved_stock")));
  }

  @Transactional
  InventoryResponse adjust(Long productId, StockAdjustmentRequest request) {
    if (request.delta() == 0) {
      throw new InventoryValidationException("Delta must not be zero");
    }

    ProductRow product = findProductForUpdate(productId);
    if (product == null) {
      throw new InventoryNotFoundException();
    }

    InventoryRow inventory = findInventoryForUpdate(productId);
    if (inventory == null) {
      if (request.delta() < 0) {
        throw conflict();
      }
      jdbcTemplate.update(
          "INSERT INTO shopflow.inventory_items (product_id) VALUES (?)", productId);
      inventory = new InventoryRow(0, 0);
    }

    long newOnHand = (long) inventory.onHandStock() + request.delta();
    if (newOnHand < inventory.reservedStock() || newOnHand < 0 || newOnHand > Integer.MAX_VALUE) {
      throw conflict();
    }

    jdbcTemplate.update(
        """
        UPDATE shopflow.inventory_items
        SET on_hand_stock = ?, updated_at = CURRENT_TIMESTAMP
        WHERE product_id = ?
        """,
        (int) newOnHand,
        productId);
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements (product_id, type, quantity, note)
        VALUES (?, 'MANUAL_ADJUSTMENT', ?, ?)
        """,
        productId,
        request.delta(),
        request.reason().strip());

    return response(product.id(), product.name(), (int) newOnHand, inventory.reservedStock());
  }

  private ProductRow findProductForUpdate(Long productId) {
    List<ProductRow> products =
        jdbcTemplate.query(
            "SELECT id, name FROM shopflow.products WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) ->
                new ProductRow(resultSet.getLong("id"), resultSet.getString("name")),
            productId);
    return products.isEmpty() ? null : products.getFirst();
  }

  private InventoryRow findInventoryForUpdate(Long productId) {
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

  private InventoryResponse response(
      Long productId, String productName, int onHandStock, int reservedStock) {
    return new InventoryResponse(
        productId, productName, onHandStock, reservedStock, onHandStock - reservedStock);
  }

  private InventoryConflictException conflict() {
    return new InventoryConflictException("Adjustment violates inventory constraints");
  }

  private record ProductRow(Long id, String name) {}

  private record InventoryRow(int onHandStock, int reservedStock) {}
}
