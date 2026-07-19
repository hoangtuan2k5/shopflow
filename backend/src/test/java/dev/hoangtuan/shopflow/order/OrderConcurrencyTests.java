package dev.hoangtuan.shopflow.order;

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
class OrderConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private OrderService orderService;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.payments");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void onlyOneConcurrentOrderCanReserveTheLastUnit() throws Exception {
    long productId = insertProduct("Last unit", "19990000");
    insertInventory(productId, 1, 0);

    CreateOrderRequest request = requestFor(productId);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<OrderResponse> first = submit(executor, start, request);
      Future<OrderResponse> second = submit(executor, start, request);

      int successfulOrders = 0;
      int insufficientOrders = 0;
      for (Future<OrderResponse> result : List.of(first, second)) {
        try {
          OrderResponse response = result.get(15, TimeUnit.SECONDS);
          successfulOrders++;
          assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
          assertThat(response.totalAmount()).isEqualByComparingTo("19990000");
        } catch (ExecutionException exception) {
          Throwable cause = exception.getCause();
          assertThat(cause).isInstanceOf(OrderValidationException.class);
          OrderValidationException validationException = (OrderValidationException) cause;
          assertThat(validationException.getInsufficientItems())
              .containsExactly(new InsufficientStock(productId, 1, 0));
          insufficientOrders++;
        }
      }

      assertThat(successfulOrders).isEqualTo(1);
      assertThat(insufficientOrders).isEqualTo(1);
      assertThat(reservedStock(productId)).isEqualTo(1);
      assertThat(onHandStock(productId)).isEqualTo(1);
      assertThat(countRows("shopflow.orders")).isEqualTo(1);
      assertThat(countRows("shopflow.order_items")).isEqualTo(1);
      assertThat(countRows("shopflow.stock_movements")).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<OrderResponse> submit(
      ExecutorService executor, CyclicBarrier start, CreateOrderRequest request) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return orderService.createOrder(request);
        });
  }

  private CreateOrderRequest requestFor(long productId) {
    return new CreateOrderRequest(
        new CreateOrderRequest.Customer("Guest", "guest@example.com", "0900000000"),
        new CreateOrderRequest.ShippingAddress(
            "Guest", "0900000000", "1 Main Street", "District 1", "Ho Chi Minh"),
        "CARD",
        List.of(new CreateOrderRequest.Item(productId, 1)));
  }

  private long insertProduct(String name, String price) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        name,
        new BigDecimal(price),
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

  private int countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
