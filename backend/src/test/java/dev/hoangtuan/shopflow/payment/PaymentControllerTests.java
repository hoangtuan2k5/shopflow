package dev.hoangtuan.shopflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

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
  void completesSuccessfulPaymentAndKeepsReservation() throws Exception {
    long productId = insertProduct("Coffee", "2190000");
    insertInventory(productId, 5, 2);
    long orderId = insertOrder("PENDING_PAYMENT", "2190000");
    insertOrderItem(orderId, productId, 2);

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.method").value("CARD"))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.amount").value(2190000))
        .andExpect(jsonPath("$.paidAt").isString())
        .andExpect(jsonPath("$.failedReason").doesNotExist())
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.orderStatus").value("PAID"));

    assertThat(orderStatus(orderId)).isEqualTo("PAID");
    assertThat(paymentStatus(orderId)).isEqualTo("SUCCESS");
    assertThat(reservedStock(productId)).isEqualTo(2);
    assertThat(onHandStock(productId)).isEqualTo(5);
    assertThat(countRows("shopflow.stock_movements")).isZero();
  }

  @Test
  void completesFailedPaymentAndReleasesReservation() throws Exception {
    long productId = insertProduct("Coffee", "2190000");
    insertInventory(productId, 5, 2);
    long orderId = insertOrder("PENDING_PAYMENT", "2190000");
    insertOrderItem(orderId, productId, 2);

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\",\"failureReason\":\"Declined by simulation\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.paidAt").doesNotExist())
        .andExpect(jsonPath("$.failedReason").value("Declined by simulation"))
        .andExpect(jsonPath("$.orderStatus").value("PAYMENT_FAILED"));

    assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_FAILED");
    assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
    assertThat(reservedStock(productId)).isZero();
    assertThat(onHandStock(productId)).isEqualTo(5);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT quantity FROM shopflow.stock_movements
                WHERE product_id = ? AND type = 'PAYMENT_FAILED_RELEASE'
                  AND reference_type = 'ORDER' AND reference_id = ?
                """,
                Integer.class,
                productId,
                orderId))
        .isEqualTo(-2);
  }

  @Test
  void rejectsMissingOrderInvalidBodyAndRepeatedPayment() throws Exception {
    mockMvc
        .perform(
            post("/orders/{orderId}/payments", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order not found"))
        .andExpect(jsonPath("$.status").value(404));

    long productId = insertProduct("Coffee", "2190000");
    insertInventory(productId, 5, 1);
    long orderId = insertOrder("PENDING_PAYMENT", "2190000");
    insertOrderItem(orderId, productId, 1);

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("failureReason is required"));

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Order is not eligible for payment"))
        .andExpect(jsonPath("$.status").value(409));

    assertThat(countRows("shopflow.payments")).isEqualTo(1);
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

  private long insertOrder(String status, String amount) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, payment_method, total_amount
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        "Guest",
        "Guest",
        "0900000000",
        "1 Main Street",
        "Hanoi",
        status,
        "CARD",
        new BigDecimal(amount));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private void insertOrderItem(long orderId, long productId, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items
            (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, ?, ?, ?)
        """,
        orderId,
        productId,
        "Coffee",
        new BigDecimal("2190000"),
        quantity);
  }

  private String orderStatus(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM shopflow.orders WHERE id = ?", String.class, orderId);
  }

  private String paymentStatus(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM shopflow.payments WHERE order_id = ?", String.class, orderId);
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
