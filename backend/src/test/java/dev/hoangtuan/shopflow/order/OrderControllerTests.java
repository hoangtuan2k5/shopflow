package dev.hoangtuan.shopflow.order;

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
class OrderControllerTests {

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
  void createsOrderWithServerSnapshotsTotalsAndReservation() throws Exception {
    long firstProductId = insertProduct("iPhone 15", "19990000", true);
    long secondProductId = insertProduct("AirPods Pro", "6990000", true);
    insertInventory(firstProductId, 5, 0);
    insertInventory(secondProductId, 3, 1);

    String body =
        """
        {
          "customer": {"fullName": "Nguyen Van A", "email": "a@example.com", "phone": "0901234567"},
          "shippingAddress": {"receiverName": "Nguyen Van A", "phone": "0901234567", "addressLine": "123 Nguyen Hue", "district": "District 1", "city": "Ho Chi Minh"},
          "paymentMethod": "CARD",
          "items": [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 1}]
        }
        """
            .formatted(firstProductId, secondProductId);

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.deliveryStatus").value("NONE"))
        .andExpect(jsonPath("$.paymentMethod").value("CARD"))
        .andExpect(jsonPath("$.totalAmount").value(46970000))
        .andExpect(jsonPath("$.customer.fullName").value("Nguyen Van A"))
        .andExpect(jsonPath("$.shippingAddress.district").value("District 1"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
        .andExpect(jsonPath("$.items[0].unitPrice").value(19990000))
        .andExpect(jsonPath("$.items[0].lineTotal").value(39980000))
        .andExpect(jsonPath("$.items[1].productName").value("AirPods Pro"))
        .andExpect(jsonPath("$.items[1].lineTotal").value(6990000))
        .andExpect(jsonPath("$.createdAt").isString());

    assertThatReservedStock(firstProductId, 2);
    assertThatReservedStock(secondProductId, 2);
    assertThatOnHandStock(firstProductId, 5);
    assertThatOnHandStock(secondProductId, 3);
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.orders")).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.order_items")).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.stock_movements")).isEqualTo(2);
  }

  @Test
  void rejectsInsufficientStockWithoutPartialOrderOrReservation() throws Exception {
    long insufficientProductId = insertProduct("Limited", "100000", true);
    long availableProductId = insertProduct("Available", "200000", true);
    insertInventory(insufficientProductId, 3, 2);
    insertInventory(availableProductId, 4, 0);

    String body =
        """
        {
          "customer": {"fullName": "Guest"},
          "shippingAddress": {"receiverName": "Guest", "phone": "0900000000", "addressLine": "1 Main", "city": "Hanoi"},
          "paymentMethod": "CARD",
          "items": [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 1}]
        }
        """
            .formatted(insufficientProductId, availableProductId);

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Insufficient stock"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.insufficientItems[0].productId").value(insufficientProductId))
        .andExpect(jsonPath("$.insufficientItems[0].requestedQuantity").value(2))
        .andExpect(jsonPath("$.insufficientItems[0].availableStock").value(1));

    assertThatReservedStock(insufficientProductId, 2);
    assertThatReservedStock(availableProductId, 0);
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.orders")).isZero();
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.order_items")).isZero();
    org.assertj.core.api.Assertions.assertThat(countRows("shopflow.stock_movements")).isZero();
  }

  @Test
  void rejectsUnavailableProductAndInvalidPaymentMethod() throws Exception {
    long inactiveProductId = insertProduct("Inactive", "100000", false);
    String body =
        """
        {
          "customer": {"fullName": "Guest"},
          "shippingAddress": {"receiverName": "Guest", "phone": "0900000000", "addressLine": "1 Main", "city": "Hanoi"},
          "paymentMethod": "CARD",
          "items": [{"productId": %d, "quantity": 1}]
        }
        """
            .formatted(inactiveProductId);

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Product not available"))
        .andExpect(jsonPath("$.unavailableProductIds[0]").value(inactiveProductId));

    String codBody = body.replace("\"CARD\"", "\"COD\"");
    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(codBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid payment method"));
  }

  @Test
  void rejectsDuplicateItemsAndFractionalStoredPrice() throws Exception {
    long productId = insertProduct("Duplicate", "100000", true);
    insertInventory(productId, 4, 0);
    String duplicateBody =
        """
        {
          "customer": {"fullName": "Guest"},
          "shippingAddress": {"receiverName": "Guest", "phone": "0900000000", "addressLine": "1 Main", "city": "Hanoi"},
          "paymentMethod": "CARD",
          "items": [{"productId": %d, "quantity": 1}, {"productId": %d, "quantity": 1}]
        }
        """
            .formatted(productId, productId);

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(duplicateBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Duplicate productId in items"));

    jdbcTemplate.update(
        "UPDATE shopflow.products SET price = ? WHERE id = ?", "100000.50", productId);
    String fractionalBody =
        duplicateBody.replace(", {\"productId\": %d, \"quantity\": 1}".formatted(productId), "");
    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(fractionalBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Product price must be a whole VND amount"));
  }

  @Test
  void rejectsMissingRequiredFields() throws Exception {
    String body =
        """
        {
          "customer": {"fullName": ""},
          "shippingAddress": {"receiverName": "Guest", "phone": "0900000000", "addressLine": "1 Main", "city": "Hanoi"},
          "paymentMethod": "CARD",
          "items": [{"productId": 1, "quantity": 1}]
        }
        """;

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid order request"))
        .andExpect(jsonPath("$.status").value(400));
  }

  private long insertProduct(String name, String price, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        name,
        new BigDecimal(price),
        active);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHandStock, int reservedStock) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHandStock,
        reservedStock);
  }

  private void assertThatReservedStock(long productId, int expected) {
    org.assertj.core.api.Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isEqualTo(expected);
  }

  private void assertThatOnHandStock(long productId, int expected) {
    org.assertj.core.api.Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isEqualTo(expected);
  }

  private int countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
