package dev.hoangtuan.shopflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class DeliveryControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.delivery_status_history");
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.payments");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void listsOnlyPaidOrdersWithItemsAndChronologicalHistory() throws Exception {
    long productId = insertProduct("Keyboard", "2190000");
    long paidOrderId = insertOrder("PAID", "PREPARING", "2190000", "Paid receiver");
    insertOrderItem(paidOrderId, productId, "Keyboard", 2);
    insertHistory(paidOrderId, "NONE", "PREPARING");
    insertHistory(paidOrderId, "PREPARING", "SHIPPED");
    insertOrder("PENDING_PAYMENT", "NONE", "1000000", "Pending receiver");

    mockMvc
        .perform(get("/deliveries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].orderId").value(paidOrderId))
        .andExpect(jsonPath("$[0].orderStatus").value("PAID"))
        .andExpect(jsonPath("$[0].deliveryStatus").value("PREPARING"))
        .andExpect(jsonPath("$[0].receiverName").value("Paid receiver"))
        .andExpect(jsonPath("$[0].city").value("Hanoi"))
        .andExpect(jsonPath("$[0].totalAmount").value(2190000))
        .andExpect(jsonPath("$[0].items[0].productId").value(productId))
        .andExpect(jsonPath("$[0].items[0].productName").value("Keyboard"))
        .andExpect(jsonPath("$[0].items[0].quantity").value(2))
        .andExpect(jsonPath("$[0].history[0].fromStatus").value("NONE"))
        .andExpect(jsonPath("$[0].history[0].toStatus").value("PREPARING"))
        .andExpect(jsonPath("$[0].history[0].changedBy").doesNotExist())
        .andExpect(jsonPath("$[0].history[1].toStatus").value("SHIPPED"));
  }

  @Test
  void advancesLifecycleAndCompletesInventoryAtomically() throws Exception {
    long firstProductId = insertProduct("Keyboard", "2000000");
    long secondProductId = insertProduct("Mouse", "500000");
    insertInventory(firstProductId, 10, 3);
    insertInventory(secondProductId, 6, 2);
    long orderId = insertOrder("PAID", "NONE", "5000000", "Receiver");
    insertOrderItem(orderId, firstProductId, "Keyboard", 2);
    insertOrderItem(orderId, secondProductId, "Mouse", 2);

    update(orderId, "PREPARING")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveryStatus").value("PREPARING"))
        .andExpect(jsonPath("$.history[0].fromStatus").value("NONE"))
        .andExpect(jsonPath("$.history[0].toStatus").value("PREPARING"));
    update(orderId, "SHIPPED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveryStatus").value("SHIPPED"));
    update(orderId, "DELIVERED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
        .andExpect(jsonPath("$.history.length()").value(3))
        .andExpect(jsonPath("$.history[2].fromStatus").value("SHIPPED"))
        .andExpect(jsonPath("$.history[2].toStatus").value("DELIVERED"));

    assertThat(deliveryStatus(orderId)).isEqualTo("DELIVERED");
    assertThat(stock(firstProductId)).containsExactly(8, 1);
    assertThat(stock(secondProductId)).containsExactly(4, 0);
    assertMovement(firstProductId, orderId, -2);
    assertMovement(secondProductId, orderId, -2);
    assertThat(countRows("shopflow.delivery_status_history")).isEqualTo(3);
  }

  @Test
  void rejectsUnpaidSkippedRepeatedAndMalformedTransitionsWithoutSideEffects() throws Exception {
    long productId = insertProduct("Keyboard", "2190000");
    insertInventory(productId, 5, 1);
    long unpaidOrderId = insertOrder("PENDING_PAYMENT", "NONE", "2190000", "Unpaid");
    insertOrderItem(unpaidOrderId, productId, "Keyboard", 1);

    update(unpaidOrderId, "PREPARING")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Order must be PAID before delivery can start"));

    long paidOrderId = insertOrder("PAID", "NONE", "2190000", "Paid");
    insertOrderItem(paidOrderId, productId, "Keyboard", 1);
    update(paidOrderId, "SHIPPED")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Invalid delivery transition: NONE -> SHIPPED"));
    update(paidOrderId, "PREPARING").andExpect(status().isOk());
    update(paidOrderId, "PREPARING").andExpect(status().isConflict());
    mockMvc
        .perform(
            patch("/orders/{orderId}/delivery", paidOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));

    assertThat(deliveryStatus(unpaidOrderId)).isEqualTo("NONE");
    assertThat(deliveryStatus(paidOrderId)).isEqualTo("PREPARING");
    assertThat(countRows("shopflow.delivery_status_history")).isEqualTo(1);
    assertThat(stock(productId)).containsExactly(5, 1);
    assertThat(countRows("shopflow.stock_movements")).isZero();
  }

  @Test
  void returnsNotFoundAndValidationErrors() throws Exception {
    update(Long.MAX_VALUE, "PREPARING")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order not found"))
        .andExpect(jsonPath("$.status").value(404));

    long orderId = insertOrder("PAID", "NONE", "1000000", "Receiver");
    mockMvc
        .perform(
            patch("/orders/{orderId}/delivery", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid delivery request"));
  }

  @Test
  void rollsBackDeliveredChangesWhenOneReservationIsInconsistent() throws Exception {
    long firstProductId = insertProduct("Keyboard", "2000000");
    long secondProductId = insertProduct("Mouse", "500000");
    insertInventory(firstProductId, 5, 1);
    insertInventory(secondProductId, 5, 0);
    long orderId = insertOrder("PAID", "SHIPPED", "2500000", "Receiver");
    insertOrderItem(orderId, firstProductId, "Keyboard", 1);
    insertOrderItem(orderId, secondProductId, "Mouse", 1);

    update(orderId, "DELIVERED")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Inventory cannot complete delivery"));

    assertThat(deliveryStatus(orderId)).isEqualTo("SHIPPED");
    assertThat(stock(firstProductId)).containsExactly(5, 1);
    assertThat(stock(secondProductId)).containsExactly(5, 0);
    assertThat(countRows("shopflow.delivery_status_history")).isZero();
    assertThat(countRows("shopflow.stock_movements")).isZero();
  }

  private org.springframework.test.web.servlet.ResultActions update(long orderId, String toStatus)
      throws Exception {
    return mockMvc.perform(
        patch("/orders/{orderId}/delivery", orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"toStatus\":\"" + toStatus + "\"}"));
  }

  private long insertProduct(String name, String price) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, TRUE)",
        name,
        new BigDecimal(price));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHand, int reserved) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHand,
        reserved);
  }

  private long insertOrder(
      String status, String deliveryStatus, String amount, String receiverName) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, delivery_status, payment_method, total_amount
        ) VALUES ('Guest', ?, '0900000000', '1 Main Street', 'Hanoi', ?, ?, 'CARD', ?)
        """,
        receiverName,
        status,
        deliveryStatus,
        new BigDecimal(amount));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private void insertOrderItem(long orderId, long productId, String productName, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items
            (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, ?, 1000000, ?)
        """,
        orderId,
        productId,
        productName,
        quantity);
  }

  private void insertHistory(long orderId, String fromStatus, String toStatus) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.delivery_status_history (order_id, from_status, to_status)
        VALUES (?, ?, ?)
        """,
        orderId,
        fromStatus,
        toStatus);
  }

  private String deliveryStatus(long orderId) {
    return jdbcTemplate.queryForObject(
        "SELECT delivery_status FROM shopflow.orders WHERE id = ?", String.class, orderId);
  }

  private java.util.List<Integer> stock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT on_hand_stock, reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
        (resultSet, rowNumber) ->
            java.util.List.of(
                resultSet.getInt("on_hand_stock"), resultSet.getInt("reserved_stock")),
        productId);
  }

  private void assertMovement(long productId, long orderId, int quantity) {
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT quantity FROM shopflow.stock_movements
                WHERE product_id = ? AND type = 'DELIVERY_COMPLETED'
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
