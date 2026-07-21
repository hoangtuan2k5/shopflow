package dev.hoangtuan.shopflow.payment;

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
class PaymentConcurrencyTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PaymentService paymentService;

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
  void onlyOneConcurrentPaymentAttemptCanComplete() throws Exception {
    long productId = insertProduct();
    insertInventory(productId);
    long orderId = insertOrder();
    insertOrderItem(orderId, productId);
    CreatePaymentRequest request = new CreatePaymentRequest(SimulatedPaymentResult.SUCCESS, null);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<PaymentResponse> first = submit(executor, start, orderId, request);
      Future<PaymentResponse> second = submit(executor, start, orderId, request);

      int successfulPayments = 0;
      int conflicts = 0;
      for (Future<PaymentResponse> result : List.of(first, second)) {
        try {
          PaymentResponse response = result.get(15, TimeUnit.SECONDS);
          successfulPayments++;
          assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
          assertThat(response.orderStatus()).isEqualTo("PAID");
        } catch (ExecutionException exception) {
          assertThat(exception.getCause()).isInstanceOf(PaymentConflictException.class);
          conflicts++;
        }
      }

      assertThat(successfulPayments).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(countRows("shopflow.payments")).isEqualTo(1);
      assertThat(orderStatus(orderId)).isEqualTo("PAID");
      assertThat(reservedStock(productId)).isEqualTo(1);
      assertThat(countRows("shopflow.stock_movements")).isZero();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Future<PaymentResponse> submit(
      ExecutorService executor, CyclicBarrier start, long orderId, CreatePaymentRequest request) {
    return executor.submit(
        () -> {
          start.await(15, TimeUnit.SECONDS);
          return paymentService.createPayment(orderId, request);
        });
  }

  private long insertProduct() {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        "Coffee",
        new BigDecimal("2190000"),
        true);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        5,
        1);
  }

  private long insertOrder() {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, payment_method, total_amount
        ) VALUES (?, ?, ?, ?, ?, 'PENDING_PAYMENT', 'CARD', ?)
        """,
        "Guest",
        "Guest",
        "0900000000",
        "1 Main Street",
        "Hanoi",
        new BigDecimal("2190000"));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private void insertOrderItem(long orderId, long productId) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items
            (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, 'Coffee', ?, 1)
        """,
        orderId,
        productId,
        new BigDecimal("2190000"));
  }

  private String orderStatus(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM shopflow.orders WHERE id = ?", String.class, orderId);
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
