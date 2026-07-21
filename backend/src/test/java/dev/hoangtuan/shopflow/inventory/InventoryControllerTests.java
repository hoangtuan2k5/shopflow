package dev.hoangtuan.shopflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTests {

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
  void listsEveryProductWithComputedStock() throws Exception {
    long activeId = insertProduct("Active", true);
    long inactiveId = insertProduct("Inactive", false);
    long missingInventoryId = insertProduct("No inventory", true);
    insertInventory(activeId, 10, 3);
    insertInventory(inactiveId, 4, 1);

    mockMvc
        .perform(get("/inventory"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].productId").value(activeId))
        .andExpect(jsonPath("$[0].onHandStock").value(10))
        .andExpect(jsonPath("$[0].reservedStock").value(3))
        .andExpect(jsonPath("$[0].availableStock").value(7))
        .andExpect(jsonPath("$[1].productId").value(inactiveId))
        .andExpect(jsonPath("$[1].availableStock").value(3))
        .andExpect(jsonPath("$[2].productId").value(missingInventoryId))
        .andExpect(jsonPath("$[2].onHandStock").value(0))
        .andExpect(jsonPath("$[2].reservedStock").value(0))
        .andExpect(jsonPath("$[2].availableStock").value(0));
  }

  @Test
  void adjustsOnHandAndWritesMovementNote() throws Exception {
    long productId = insertProduct("Keyboard", true);
    insertInventory(productId, 10, 3);

    adjust(productId, -2, "  Damaged during stock count  ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onHandStock").value(8))
        .andExpect(jsonPath("$.reservedStock").value(3))
        .andExpect(jsonPath("$.availableStock").value(5));

    assertThat(onHandStock(productId)).isEqualTo(8);
    assertThat(movementQuantity(productId)).isEqualTo(-2);
    assertThat(movementNote(productId)).isEqualTo("Damaged during stock count");
  }

  @Test
  void createsMissingInventoryForPositiveAdjustment() throws Exception {
    long productId = insertProduct("New stock", true);

    adjust(productId, 5, "Initial count")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onHandStock").value(5))
        .andExpect(jsonPath("$.reservedStock").value(0))
        .andExpect(jsonPath("$.availableStock").value(5));

    assertThat(onHandStock(productId)).isEqualTo(5);
    assertThat(movementQuantity(productId)).isEqualTo(5);
  }

  @Test
  void rejectsAdjustmentThatWouldDropBelowReserved() throws Exception {
    long productId = insertProduct("Reserved", true);
    insertInventory(productId, 10, 8);

    adjust(productId, -3, "Count correction")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Adjustment violates inventory constraints"));

    assertThat(onHandStock(productId)).isEqualTo(10);
    assertThat(movementCount()).isZero();
  }

  @Test
  void rejectsInvalidAdjustmentBodies() throws Exception {
    long productId = insertProduct("Invalid", true);
    insertInventory(productId, 3, 0);

    adjust(productId, 0, "No change")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Delta must not be zero"));
    mockMvc
        .perform(
            post("/inventory/{productId}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":1,\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").isString());
    mockMvc
        .perform(
            post("/inventory/{productId}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":1.5,\"reason\":\"Invalid fraction\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));

    assertThat(onHandStock(productId)).isEqualTo(3);
    assertThat(movementCount()).isZero();
  }

  @Test
  void returnsNotFoundWithoutCreatingInventory() throws Exception {
    adjust(Long.MAX_VALUE, 1, "Missing product")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"));

    assertThat(inventoryCount()).isZero();
    assertThat(movementCount()).isZero();
  }

  private org.springframework.test.web.servlet.ResultActions adjust(
      long productId, int delta, String reason) throws Exception {
    String body = "{\"delta\":%d,\"reason\":\"%s\"}".formatted(delta, reason);
    return mockMvc.perform(
        post("/inventory/{productId}/adjustments", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private long insertProduct(String name, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)",
        name,
        new BigDecimal("1000"),
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

  private int onHandStock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int movementQuantity(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT quantity FROM shopflow.stock_movements WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private String movementNote(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT note FROM shopflow.stock_movements WHERE product_id = ?", String.class, productId);
  }

  private int inventoryCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.inventory_items", Integer.class);
  }

  private int movementCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.stock_movements", Integer.class);
  }
}
