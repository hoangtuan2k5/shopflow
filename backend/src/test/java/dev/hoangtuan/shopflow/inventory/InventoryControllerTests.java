package dev.hoangtuan.shopflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class InventoryControllerTests {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void buildMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .defaultRequest(get("/").with(csrf()).with(user("tester").roles("WAREHOUSE")))
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
  void flagsLowStockAtOrBelowThresholdOnly() throws Exception {
    long atThresholdId = insertProduct("At threshold", true);
    long aboveThresholdId = insertProduct("Above threshold", true);
    long untrackedId = insertProduct("Untracked", true);
    long zeroThresholdId = insertProduct("Zero threshold", true);
    insertInventory(atThresholdId, 10, 5);
    insertInventory(aboveThresholdId, 11, 5);
    insertInventory(zeroThresholdId, 2, 2);
    setThreshold(atThresholdId, 5);
    setThreshold(aboveThresholdId, 5);
    setThreshold(zeroThresholdId, 0);

    mockMvc
        .perform(get("/inventory"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].lowStockThreshold").value(5))
        .andExpect(jsonPath("$[0].lowStock").value(true))
        .andExpect(jsonPath("$[1].lowStockThreshold").value(5))
        .andExpect(jsonPath("$[1].lowStock").value(false))
        .andExpect(jsonPath("$[2].lowStockThreshold").isEmpty())
        .andExpect(jsonPath("$[2].lowStock").value(false))
        .andExpect(jsonPath("$[3].lowStockThreshold").value(0))
        .andExpect(jsonPath("$[3].lowStock").value(true));
  }

  @Test
  void updatesThresholdAndRecomputesFlagWithoutMovements() throws Exception {
    long productId = insertProduct("Tracked", true);
    insertInventory(productId, 4, 0);

    putThreshold(productId, "{\"lowStockThreshold\":5}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lowStockThreshold").value(5))
        .andExpect(jsonPath("$.lowStock").value(true));
    assertThat(thresholdInDatabase(productId)).isEqualTo(5);

    adjust(productId, 5, "Recount")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableStock").value(9))
        .andExpect(jsonPath("$.lowStockThreshold").value(5))
        .andExpect(jsonPath("$.lowStock").value(false));

    putThreshold(productId, "{\"lowStockThreshold\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lowStockThreshold").isEmpty())
        .andExpect(jsonPath("$.lowStock").value(false));
    assertThat(thresholdInDatabase(productId)).isNull();
    assertThat(movementCount()).isEqualTo(1);
  }

  @Test
  void rejectsInvalidThresholdRequests() throws Exception {
    long productId = insertProduct("Threshold validation", true);
    setThreshold(productId, 3);

    putThreshold(productId, "{\"lowStockThreshold\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.lowStockThreshold").isString());
    putThreshold(productId, "{\"lowStockThreshold\":1.5}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));
    putThreshold(Long.MAX_VALUE, "{\"lowStockThreshold\":2}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"));

    assertThat(thresholdInDatabase(productId)).isEqualTo(3);
    assertThat(movementCount()).isZero();
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
    assertThat(movementType(productId)).isEqualTo("MANUAL_ADJUSTMENT");
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

  @Test
  void rejectsNegativeMissingInventoryAndIntegerOverflow() throws Exception {
    long missingInventoryId = insertProduct("No inventory", true);

    adjust(missingInventoryId, -1, "Negative initial count").andExpect(status().isConflict());
    assertThat(inventoryCount()).isZero();
    assertThat(movementCount()).isZero();

    long maximumId = insertProduct("Maximum", true);
    insertInventory(maximumId, Integer.MAX_VALUE, 0);
    adjust(maximumId, 1, "Overflow")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Adjustment violates inventory constraints"));

    assertThat(onHandStock(maximumId)).isEqualTo(Integer.MAX_VALUE);
    assertThat(movementCount()).isZero();
  }

  @Test
  void rejectsMissingDeltaAndOverlongReason() throws Exception {
    long productId = insertProduct("Validation", true);
    insertInventory(productId, 3, 0);

    mockMvc
        .perform(
            post("/inventory/{productId}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Missing delta\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.delta").isString());

    String body = "{\"delta\":1,\"reason\":\"%s\"}".formatted("x".repeat(501));
    mockMvc
        .perform(
            post("/inventory/{productId}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").isString());

    assertThat(onHandStock(productId)).isEqualTo(3);
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

  private org.springframework.test.web.servlet.ResultActions putThreshold(
      long productId, String body) throws Exception {
    return mockMvc.perform(
        put("/inventory/{productId}/threshold", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void setThreshold(long productId, Integer threshold) {
    jdbcTemplate.update(
        "UPDATE shopflow.products SET low_stock_threshold = ? WHERE id = ?", threshold, productId);
  }

  private Integer thresholdInDatabase(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT low_stock_threshold FROM shopflow.products WHERE id = ?", Integer.class, productId);
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

  private String movementType(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT type FROM shopflow.stock_movements WHERE product_id = ?", String.class, productId);
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
