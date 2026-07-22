package dev.hoangtuan.shopflow.receiving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
class ReceivingConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ReceivingService receivingService;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.receiving_records");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void concurrentReceiptsForExistingInventoryDoNotLoseStock() throws Exception {
    long productId = insertProduct("Existing inventory");
    insertInventory(productId, 10, 2);

    submitTogether(productId, 3, 4);

    assertThat(onHandStock(productId)).isEqualTo(17);
    assertThat(reservedStock(productId)).isEqualTo(2);
    assertThat(inventoryCount(productId)).isEqualTo(1);
    assertThat(receivingCount(productId)).isEqualTo(2);
    assertThat(movementCount(productId)).isEqualTo(2);
  }

  @Test
  void concurrentFirstReceiptsCreateOneInventoryRow() throws Exception {
    long productId = insertProduct("Missing inventory");

    submitTogether(productId, 3, 4);

    assertThat(onHandStock(productId)).isEqualTo(7);
    assertThat(reservedStock(productId)).isZero();
    assertThat(inventoryCount(productId)).isEqualTo(1);
    assertThat(receivingCount(productId)).isEqualTo(2);
    assertThat(movementCount(productId)).isEqualTo(2);
  }

  @Test
  void auditFailureRollsBackInventoryAndReceivingRecord() {
    long productId = insertProduct("Audit failure");
    jdbcTemplate.execute(
        """
        ALTER TABLE shopflow.stock_movements
        ADD CONSTRAINT prevent_stock_received CHECK (type <> 'STOCK_RECEIVED')
        """);

    try {
      assertThatThrownBy(
              () -> receivingService.receive(new ReceivingRequest(productId, 3, null, null)))
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(inventoryCount(productId)).isZero();
      assertThat(receivingCount(productId)).isZero();
      assertThat(movementCount(productId)).isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE shopflow.stock_movements DROP CONSTRAINT prevent_stock_received");
    }
  }

  private void submitTogether(long productId, int firstQuantity, int secondQuantity)
      throws Exception {
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      List<Future<ReceivingResponse>> results =
          List.of(
              submit(executor, start, productId, firstQuantity),
              submit(executor, start, productId, secondQuantity));
      for (Future<ReceivingResponse> result : results) {
        assertThat(result.get(15, TimeUnit.SECONDS).createdBy()).isNull();
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<ReceivingResponse> submit(
      ExecutorService executor, CyclicBarrier start, long productId, int quantity) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return receivingService.receive(new ReceivingRequest(productId, quantity, null, null));
        });
  }

  private long insertProduct(String name) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        name,
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

  private int inventoryCount(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int receivingCount(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.receiving_records WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int movementCount(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.stock_movements WHERE product_id = ?",
        Integer.class,
        productId);
  }
}
