package dev.hoangtuan.shopflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.order.DeliveryStatus;
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
class DeliveryConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DeliveryService deliveryService;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.delivery_status_history");
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void concurrentCompletionDecrementsInventoryOnlyOnce() throws Exception {
    long productId = insertProduct();
    insertInventory(productId);
    long orderId = insertOrder();
    insertOrderItem(orderId, productId);
    UpdateDeliveryRequest request = new UpdateDeliveryRequest(DeliveryStatus.DELIVERED);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<DeliveryResponse> first = submit(executor, start, orderId, request);
      Future<DeliveryResponse> second = submit(executor, start, orderId, request);

      int successes = 0;
      int conflicts = 0;
      for (Future<DeliveryResponse> result : List.of(first, second)) {
        try {
          assertThat(result.get(15, TimeUnit.SECONDS).deliveryStatus())
              .isEqualTo(DeliveryStatus.DELIVERED);
          successes++;
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(DeliveryConflictException.class);
          conflicts++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(deliveryStatus(orderId)).isEqualTo("DELIVERED");
      assertThat(onHandStock(productId)).isEqualTo(8);
      assertThat(reservedStock(productId)).isZero();
      assertThat(countRows("shopflow.delivery_status_history")).isEqualTo(1);
      assertThat(countRows("shopflow.stock_movements")).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<DeliveryResponse> submit(
      ExecutorService executor, CyclicBarrier start, long orderId, UpdateDeliveryRequest request) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return deliveryService.update(orderId, request);
        });
  }

  private long insertProduct() {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES ('Keyboard', ?, TRUE)",
        new BigDecimal("2000000"));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, 10, 2)",
        productId);
  }

  private long insertOrder() {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, delivery_status, payment_method, total_amount
        ) VALUES ('Guest', 'Guest', '0900000000', '1 Main Street', 'Hanoi',
                  'PAID', 'SHIPPED', 'CARD', 4000000)
        """);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private void insertOrderItem(long orderId, long productId) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items
            (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, 'Keyboard', 2000000, 2)
        """,
        orderId,
        productId);
  }

  private String deliveryStatus(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT delivery_status FROM shopflow.orders WHERE id = ?", String.class, orderId);
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

  private int countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
