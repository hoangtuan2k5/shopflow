package dev.hoangtuan.shopflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
class InventoryConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private InventoryService inventoryService;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void concurrentAdjustmentsPreserveReservedStockInvariant() throws Exception {
    long productId = insertProduct();
    insertInventory(productId, 9, 8);
    StockAdjustmentRequest request = new StockAdjustmentRequest(-1, "Concurrent count");
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<InventoryResponse> first = submit(executor, start, productId, request);
      Future<InventoryResponse> second = submit(executor, start, productId, request);

      int successes = 0;
      int conflicts = 0;
      for (Future<InventoryResponse> result : List.of(first, second)) {
        try {
          InventoryResponse response = result.get(15, TimeUnit.SECONDS);
          successes++;
          assertThat(response.onHandStock()).isEqualTo(8);
          assertThat(response.reservedStock()).isEqualTo(8);
          assertThat(response.availableStock()).isZero();
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(InventoryConflictException.class);
          conflicts++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(onHandStock(productId)).isEqualTo(8);
      assertThat(reservedStock(productId)).isEqualTo(8);
      assertThat(movementCount()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<InventoryResponse> submit(
      ExecutorService executor,
      CyclicBarrier start,
      long productId,
      StockAdjustmentRequest request) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return inventoryService.adjust(productId, request);
        });
  }

  private long insertProduct() {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        "Concurrent stock",
        new BigDecimal("1000"),
        true);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
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

  private int movementCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.stock_movements", Integer.class);
  }
}
