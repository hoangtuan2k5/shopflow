package dev.hoangtuan.shopflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTests {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void buildMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .defaultRequest(get("/").with(csrf()))
            .build();
  }

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
            post("/orders/{orderRef}/payments", orderRef(orderId))
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
            post("/orders/{orderRef}/payments", orderRef(orderId))
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
  void refusesToPayForAnOrderAddressedByItsSequentialId() throws Exception {
    long productId = insertProduct("Coffee", "2190000");
    insertInventory(productId, 5, 1);
    long orderId = insertOrder("PENDING_PAYMENT", "2190000");
    insertOrderItem(orderId, productId, 1);

    // Đường tấn công cũ: dò id tuần tự để đánh dấu đã thanh toán hoặc ép thất bại
    // nhằm giải phóng tồn kho đang giữ của đơn người khác.
    for (String guess : new String[] {String.valueOf(orderId), "1", "999999"}) {
      mockMvc
          .perform(
              post("/orders/{orderRef}/payments", guess)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"result\":\"FAILED\",\"failureReason\":\"Declined\"}"))
          .andExpect(status().isNotFound());
    }

    assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    assertThat(countPayments()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isEqualTo(1);
  }

  @Test
  void givesEveryOrderAnUnguessableReferenceDistinctFromItsId() {
    long first = insertOrder("PENDING_PAYMENT", "2190000");
    long second = insertOrder("PENDING_PAYMENT", "2190000");

    String firstRef = orderRef(first);
    String secondRef = orderRef(second);

    // Hai đơn liền kề nhau về id nhưng tham chiếu không liền kề: biết một cái không suy ra cái kia.
    assertThat(second).isEqualTo(first + 1);
    assertThat(firstRef)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    assertThat(secondRef)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        .isNotEqualTo(firstRef);
  }

  @Test
  void rejectsMissingOrderInvalidBodyAndRepeatedPayment() throws Exception {
    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", "00000000-0000-0000-0000-000000000000")
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
            post("/orders/{orderRef}/payments", orderRef(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("failureReason is required"));

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Order is not eligible for payment"))
        .andExpect(jsonPath("$.status").value(409));

    assertThat(countRows("shopflow.payments")).isEqualTo(1);
  }

  @Test
  void expiresPaymentAndReleasesEachProductWithoutChangingOnHand() throws Exception {
    long firstProductId = insertProduct("Coffee", "2190000");
    long secondProductId = insertProduct("Tea", "1000000");
    insertInventory(firstProductId, 10, 3);
    insertInventory(secondProductId, 5, 2);
    long orderId = insertOrder("PENDING_PAYMENT", "5380000");
    insertOrderItem(orderId, firstProductId, 2);
    insertOrderItem(orderId, secondProductId, 1);

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"EXPIRED\",\"failureReason\":\"Expired by simulation\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"))
        .andExpect(jsonPath("$.orderStatus").value("PAYMENT_FAILED"));

    assertThat(reservedStock(firstProductId)).isEqualTo(1);
    assertThat(reservedStock(secondProductId)).isEqualTo(1);
    assertThat(onHandStock(firstProductId)).isEqualTo(10);
    assertThat(onHandStock(secondProductId)).isEqualTo(5);
    assertReleaseMovement(firstProductId, orderId, -2);
    assertReleaseMovement(secondProductId, orderId, -1);
  }

  @Test
  void rejectsInvalidStateAndFailureReasonShapesWithoutCreatingPayment() throws Exception {
    long productId = insertProduct("Coffee", "2190000");
    insertInventory(productId, 5, 1);
    long paidOrderId = insertOrder("PAID", "2190000");
    insertOrderItem(paidOrderId, productId, 1);

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(paidOrderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\"}"))
        .andExpect(status().isConflict());

    long pendingOrderId = insertOrder("PENDING_PAYMENT", "2190000");
    insertOrderItem(pendingOrderId, productId, 1);

    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(pendingOrderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"SUCCESS\",\"failureReason\":\"unexpected\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("failureReason is not allowed for success"));

    String overlongReason = "x".repeat(501);
    mockMvc
        .perform(
            post("/orders/{orderRef}/payments", orderRef(pendingOrderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"FAILED\",\"failureReason\":\"" + overlongReason + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid payment request"));

    assertThat(countRows("shopflow.payments")).isZero();
    assertThat(orderStatus(pendingOrderId)).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void rollsBackPaymentAndEarlierReleasesWhenOneReservationIsInconsistent() {
    long firstProductId = insertProduct("Coffee", "2190000");
    long secondProductId = insertProduct("Tea", "1000000");
    insertInventory(firstProductId, 5, 1);
    insertInventory(secondProductId, 5, 0);
    long orderId = insertOrder("PENDING_PAYMENT", "3190000");
    insertOrderItem(orderId, firstProductId, 1);
    insertOrderItem(orderId, secondProductId, 1);

    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/orders/{orderRef}/payments", orderRef(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"result\":\"FAILED\",\"failureReason\":\"Declined by simulation\"}")))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Reserved stock is inconsistent");

    assertThat(countRows("shopflow.payments")).isZero();
    assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
    assertThat(reservedStock(firstProductId)).isEqualTo(1);
    assertThat(reservedStock(secondProductId)).isZero();
    assertThat(countRows("shopflow.stock_movements")).isZero();
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

  private String orderRef(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT order_ref FROM shopflow.orders WHERE id = ?", String.class, orderId);
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

  private int countPayments() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shopflow.payments", Integer.class);
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

  private void assertReleaseMovement(long productId, long orderId, int quantity) {
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
        .isEqualTo(quantity);
  }

  private int countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
