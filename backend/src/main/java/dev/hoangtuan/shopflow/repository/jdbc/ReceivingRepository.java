package dev.hoangtuan.shopflow.repository.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReceivingRepository {
  private final JdbcTemplate jdbcTemplate;

  public ReceivingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public ProductRow findProductForUpdate(Long productId) {
    List<ProductRow> products =
        jdbcTemplate.query(
            "SELECT id, name FROM shopflow.products WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) ->
                new ProductRow(resultSet.getLong("id"), resultSet.getString("name")),
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

  public ReceivingRecord insertReceiving(
      Long productId, int quantity, String supplierName, String note) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO shopflow.receiving_records (product_id, quantity, supplier_name, note)
                  VALUES (?, ?, ?, ?)
                  """,
                  new String[] {"id"});
          statement.setLong(1, productId);
          statement.setInt(2, quantity);
          statement.setString(3, supplierName);
          statement.setString(4, note);
          return statement;
        },
        keyHolder);
    Number id = keyHolder.getKey();
    if (id == null) {
      throw new IllegalStateException("Receiving record was not created");
    }

    return jdbcTemplate.queryForObject(
        "SELECT id, created_at FROM shopflow.receiving_records WHERE id = ?",
        (resultSet, rowNumber) ->
            new ReceivingRecord(
                resultSet.getLong("id"), instant(resultSet.getTimestamp("created_at"))),
        id.longValue());
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp.toInstant();
  }

  public record ProductRow(Long id, String name) {}

  public record InventoryRow(int onHandStock, int reservedStock) {}

  public record ReceivingRecord(Long id, Instant createdAt) {}

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

  public void recordReceiving(Long productId, int quantity, Long receivingId, String note) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements
            (product_id, type, quantity, reference_type, reference_id, note)
        VALUES (?, 'STOCK_RECEIVED', ?, 'RECEIVING', ?, ?)
        """,
        productId,
        quantity,
        receivingId,
        note);
  }
}
