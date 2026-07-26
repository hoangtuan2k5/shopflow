package dev.hoangtuan.shopflow.customerreturn;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
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
class ReturnConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ReturnService returnService;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.return_request_items");
    jdbcTemplate.update("DELETE FROM shopflow.return_requests");
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void concurrentCreatesNeverExceedPurchasedQuantity() throws Exception {
    long productId = insertProduct();
    insertInventory(productId, 10, 0);
    long orderId = insertOrder();
    long orderItemId = insertOrderItem(orderId, productId, 3);
    CreateReturnRequest request =
        new CreateReturnRequest(orderId, null, List.of(new ReturnItemRequest(orderItemId, 2)));
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<ReturnResponse> first = submit(executor, start, () -> returnService.create(request));
      Future<ReturnResponse> second = submit(executor, start, () -> returnService.create(request));

      int successes = 0;
      int conflicts = 0;
      for (Future<ReturnResponse> result : List.of(first, second)) {
        try {
          assertThat(result.get(15, TimeUnit.SECONDS).status()).isEqualTo(ReturnStatus.REQUESTED);
          successes++;
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(ReturnConflictException.class);
          conflicts++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(countRows("shopflow.return_requests")).isEqualTo(1);
      assertThat(totalReturnedQuantity(orderItemId)).isEqualTo(2);
      assertThat(countRows("shopflow.stock_movements")).isZero();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void concurrentRestockConfirmationsIncreaseOnHandOnlyOnce() throws Exception {
    long productId = insertProduct();
    insertInventory(productId, 10, 0);
    long orderId = insertOrder();
    long orderItemId = insertOrderItem(orderId, productId, 3);
    ReturnResponse created =
        returnService.create(
            new CreateReturnRequest(orderId, null, List.of(new ReturnItemRequest(orderItemId, 2))));
    returnService.update(created.id(), new UpdateReturnRequest(ReturnStatus.APPROVED, true));
    UpdateReturnRequest restock = new UpdateReturnRequest(ReturnStatus.RESTOCKED, null);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<ReturnResponse> first =
          submit(executor, start, () -> returnService.update(created.id(), restock));
      Future<ReturnResponse> second =
          submit(executor, start, () -> returnService.update(created.id(), restock));

      int successes = 0;
      int conflicts = 0;
      for (Future<ReturnResponse> result : List.of(first, second)) {
        try {
          assertThat(result.get(15, TimeUnit.SECONDS).status()).isEqualTo(ReturnStatus.RESTOCKED);
          successes++;
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(ReturnConflictException.class);
          conflicts++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(returnStatus(created.id())).isEqualTo("RESTOCKED");
      assertThat(onHandStock(productId)).isEqualTo(12);
      assertThat(countRows("shopflow.stock_movements")).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<ReturnResponse> submit(
      ExecutorService executor, CyclicBarrier start, Callable<ReturnResponse> call) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return call.call();
        });
  }

  private long insertProduct() {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES ('Keyboard', ?, TRUE)",
        new BigDecimal("2000000"));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHandStock, int reservedStock) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHandStock,
        reservedStock);
  }

  private long insertOrder() {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, delivery_status, payment_method, total_amount
        ) VALUES ('Guest', 'Guest', '0900000000', '1 Main Street', 'Hanoi',
                  'PAID', 'DELIVERED', 'CARD', 6000000)
        """);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private long insertOrderItem(long orderId, long productId, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items
            (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, 'Keyboard', 2000000, ?)
        """,
        orderId,
        productId,
        quantity);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.order_items", Long.class);
  }

  private int totalReturnedQuantity(long orderItemId) {
    return jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(quantity), 0) FROM shopflow.return_request_items WHERE order_item_id = ?",
        Integer.class,
        orderItemId);
  }

  private String returnStatus(long returnId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM shopflow.return_requests WHERE id = ?", String.class, returnId);
  }

  private int onHandStock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
