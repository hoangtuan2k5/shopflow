package dev.hoangtuan.shopflow.receiving;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReceivingService {

  private final JdbcTemplate jdbcTemplate;

  ReceivingService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  ReceivingResponse receive(ReceivingRequest request) {
    ProductRow product = findProductForUpdate(request.productId());
    if (product == null) {
      throw new ReceivingNotFoundException();
    }

    String supplierName = optionalText(request.supplierName(), "supplierName", 255);
    String note = optionalText(request.note(), "note", 500);
    InventoryRow inventory = findInventoryForUpdate(product.id());
    if (inventory == null) {
      jdbcTemplate.update(
          "INSERT INTO shopflow.inventory_items (product_id) VALUES (?)", product.id());
      inventory = new InventoryRow(0, 0);
    }

    long newOnHand = (long) inventory.onHandStock() + request.quantity();
    if (newOnHand > Integer.MAX_VALUE) {
      throw new ReceivingConflictException("Receiving exceeds inventory limit");
    }

    jdbcTemplate.update(
        """
        UPDATE shopflow.inventory_items
        SET on_hand_stock = ?, updated_at = CURRENT_TIMESTAMP
        WHERE product_id = ?
        """,
        (int) newOnHand,
        product.id());
    ReceivingRecord record = insertReceiving(product.id(), request.quantity(), supplierName, note);
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.stock_movements
            (product_id, type, quantity, reference_type, reference_id, note)
        VALUES (?, 'STOCK_RECEIVED', ?, 'RECEIVING', ?, ?)
        """,
        product.id(),
        request.quantity(),
        record.id(),
        note);

    return new ReceivingResponse(
        record.id(),
        product.id(),
        product.name(),
        request.quantity(),
        supplierName,
        note,
        record.createdAt(),
        null,
        (int) newOnHand,
        inventory.reservedStock(),
        (int) newOnHand - inventory.reservedStock());
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

  private ReceivingRecord insertReceiving(
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

  private String optionalText(String value, String field, int maximumLength) {
    if (value == null) {
      return null;
    }

    String text = value.strip();
    if (text.isEmpty()) {
      throw new ReceivingValidationException(field, "Must not be blank");
    }
    if (text.length() > maximumLength) {
      throw new ReceivingValidationException(
          field, "Must be %d characters or fewer".formatted(maximumLength));
    }
    return text;
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp.toInstant();
  }

  private record ProductRow(Long id, String name) {}

  private record InventoryRow(int onHandStock, int reservedStock) {}

  private record ReceivingRecord(Long id, Instant createdAt) {}
}
