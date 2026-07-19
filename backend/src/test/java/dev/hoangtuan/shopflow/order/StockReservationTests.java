package dev.hoangtuan.shopflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
class StockReservationTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private StockReservationService stockReservationService;

  @Autowired private TransactionTemplate transactionTemplate;

  private final List<Long> fixtureProductIds = new ArrayList<>();

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @AfterEach
  void removeFixtures() {
    for (Long productId : fixtureProductIds) {
      jdbcTemplate.update("DELETE FROM shopflow.stock_movements WHERE product_id = ?", productId);
      jdbcTemplate.update("DELETE FROM shopflow.inventory_items WHERE product_id = ?", productId);
      jdbcTemplate.update("DELETE FROM shopflow.products WHERE id = ?", productId);
    }
    fixtureProductIds.clear();
  }

  @Test
  void reportsAllUnavailableAndInsufficientItemsWithoutUpdatingStock() {
    long insufficientId = insertProduct("Insufficient", true);
    long inactiveId = insertProduct("Inactive", false);
    long missingInventoryId = insertProduct("Missing inventory", true);
    insertInventory(insufficientId, 5, 2);

    StockValidationResult result =
        transactionTemplate.execute(
            status ->
                stockReservationService.lockAndValidate(
                    List.of(
                        new StockRequest(insufficientId, 4),
                        new StockRequest(inactiveId, 1),
                        new StockRequest(missingInventoryId, 1),
                        new StockRequest(Long.MAX_VALUE, 1))));

    assertThat(result.unavailableProductIds())
        .containsExactly(inactiveId, missingInventoryId, Long.MAX_VALUE);
    assertThat(result.insufficientItems())
        .containsExactly(new InsufficientStock(insufficientId, 4, 3));
    assertThat(result.isValid()).isFalse();
    assertThat(reservedStock(insufficientId)).isEqualTo(2);
  }

  @Test
  void reservesStockWithoutChangingOnHandAndWritesMovement() {
    long productId = insertProduct("Coffee", true);
    insertInventory(productId, 10, 2);

    StockValidationResult result =
        transactionTemplate.execute(
            status -> {
              StockValidationResult validation =
                  stockReservationService.lockAndValidate(List.of(new StockRequest(productId, 3)));
              stockReservationService.reserve(42L, validation.items());
              return validation;
            });

    assertThat(result.isValid()).isTrue();
    assertThat(onHandStock(productId)).isEqualTo(10);
    assertThat(reservedStock(productId)).isEqualTo(5);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shopflow.stock_movements "
                    + "WHERE product_id = ? AND type = 'ORDER_RESERVED' "
                    + "AND reference_id = ?",
                Integer.class,
                productId,
                42L))
        .isEqualTo(1);
  }

  @Test
  void rollsBackReservationWhenOuterTransactionFails() {
    long productId = insertProduct("Rollback", true);
    insertInventory(productId, 4, 0);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      StockValidationResult validation =
                          stockReservationService.lockAndValidate(
                              List.of(new StockRequest(productId, 2)));
                      stockReservationService.reserve(43L, validation.items());
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(reservedStock(productId)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shopflow.stock_movements WHERE reference_id = ?",
                Integer.class,
                43L))
        .isZero();
  }

  @Test
  void rejectsDuplicateProductRequestsBeforeLocking() {
    long productId = insertProduct("Duplicate", true);
    insertInventory(productId, 2, 0);

    assertThatThrownBy(
            () ->
                transactionTemplate.execute(
                    status ->
                        stockReservationService.lockAndValidate(
                            List.of(
                                new StockRequest(productId, 1), new StockRequest(productId, 1)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate product id");
  }

  private long insertProduct(String name, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        name,
        new BigDecimal("1000"),
        active);
    long productId =
        jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
    fixtureProductIds.add(productId);
    return productId;
  }

  private void insertInventory(long productId, int onHandStock, int reservedStock) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHandStock,
        reservedStock);
  }

  private int onHandStock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int reservedStock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }
}
