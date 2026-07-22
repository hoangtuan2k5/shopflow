package dev.hoangtuan.shopflow.receiving;

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
import org.springframework.test.web.servlet.ResultActions;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ReceivingControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.receiving_records");
    jdbcTemplate.update("DELETE FROM shopflow.payments");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void receivesActiveAndInactiveProductsWithAnAuditReference() throws Exception {
    long activeId = insertProduct("Keyboard", true);
    long inactiveId = insertProduct("Retired keyboard", false);
    insertInventory(activeId, 10, 3);

    receive(activeId, 5, "  Acme Distribution  ", "  Invoice INV-1  ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.productId").value(activeId))
        .andExpect(jsonPath("$.productName").value("Keyboard"))
        .andExpect(jsonPath("$.quantity").value(5))
        .andExpect(jsonPath("$.supplierName").value("Acme Distribution"))
        .andExpect(jsonPath("$.note").value("Invoice INV-1"))
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.createdBy").isEmpty())
        .andExpect(jsonPath("$.onHandStock").value(15))
        .andExpect(jsonPath("$.reservedStock").value(3))
        .andExpect(jsonPath("$.availableStock").value(12));

    receive(inactiveId, 2, null, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.onHandStock").value(2))
        .andExpect(jsonPath("$.reservedStock").value(0));

    assertThat(onHandStock(activeId)).isEqualTo(15);
    assertThat(onHandStock(inactiveId)).isEqualTo(2);
    assertThat(movementType(activeId)).isEqualTo("STOCK_RECEIVED");
    assertThat(movementReferenceType(activeId)).isEqualTo("RECEIVING");
    assertThat(movementReferenceId(activeId)).isEqualTo(receivingId(activeId));
    assertThat(receivingCreatedBy(activeId)).isNull();
    assertThat(movementCreatedBy(activeId)).isNull();
  }

  @Test
  void createsMissingInventoryBeforeReceiving() throws Exception {
    long productId = insertProduct("New stock", true);

    receive(productId, 4, null, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.onHandStock").value(4))
        .andExpect(jsonPath("$.reservedStock").value(0))
        .andExpect(jsonPath("$.availableStock").value(4));

    assertThat(onHandStock(productId)).isEqualTo(4);
    assertThat(receivingCount()).isEqualTo(1);
    assertThat(movementCount()).isEqualTo(1);
  }

  @Test
  void rejectsInvalidBodiesWithoutSideEffects() throws Exception {
    long productId = insertProduct("Validation", true);
    insertInventory(productId, 3, 0);

    receive(productId, 0, null, null).andExpect(status().isBadRequest());
    receive(productId, -1, null, null).andExpect(status().isBadRequest());
    rawReceive("{\"productId\":%d,\"quantity\":1.5}".formatted(productId))
        .andExpect(status().isBadRequest());
    rawReceive("{\"productId\":\"%d\",\"quantity\":1}".formatted(productId))
        .andExpect(status().isBadRequest());
    rawReceive("{\"productId\":%d,\"quantity\":\"1\"}".formatted(productId))
        .andExpect(status().isBadRequest());
    rawReceive("{\"productId\":%d,\"quantity\":1,\"supplierName\":\"  \"}".formatted(productId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.supplierName").isString());
    rawReceive(
            "{\"productId\":%d,\"quantity\":1,\"note\":\"%s\"}"
                .formatted(productId, "x".repeat(501)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.note").isString());

    assertThat(onHandStock(productId)).isEqualTo(3);
    assertThat(receivingCount()).isZero();
    assertThat(movementCount()).isZero();
  }

  @Test
  void rejectsMissingProductsAndInventoryOverflowWithoutWriting() throws Exception {
    receive(Long.MAX_VALUE, 1, null, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"));

    long productId = insertProduct("Maximum", true);
    insertInventory(productId, Integer.MAX_VALUE, 0);
    receive(productId, 1, null, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Receiving exceeds inventory limit"));

    assertThat(onHandStock(productId)).isEqualTo(Integer.MAX_VALUE);
    assertThat(receivingCount()).isZero();
    assertThat(movementCount()).isZero();
  }

  private ResultActions receive(long productId, int quantity, String supplierName, String note)
      throws Exception {
    String body =
        "{\"productId\":%d,\"quantity\":%d%s%s}"
            .formatted(
                productId,
                quantity,
                supplierName == null ? "" : ",\"supplierName\":\"%s\"".formatted(supplierName),
                note == null ? "" : ",\"note\":\"%s\"".formatted(note));
    return rawReceive(body);
  }

  private ResultActions rawReceive(String body) throws Exception {
    return mockMvc.perform(
        post("/receivings").contentType(MediaType.APPLICATION_JSON).content(body));
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

  private long receivingId(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM shopflow.receiving_records WHERE product_id = ?", Long.class, productId);
  }

  private int receivingCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.receiving_records", Integer.class);
  }

  private int movementCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.stock_movements", Integer.class);
  }

  private String movementType(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT type FROM shopflow.stock_movements WHERE product_id = ?", String.class, productId);
  }

  private String movementReferenceType(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT reference_type FROM shopflow.stock_movements WHERE product_id = ?",
        String.class,
        productId);
  }

  private long movementReferenceId(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT reference_id FROM shopflow.stock_movements WHERE product_id = ?",
        Long.class,
        productId);
  }

  private String receivingCreatedBy(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT created_by FROM shopflow.receiving_records WHERE product_id = ?",
        String.class,
        productId);
  }

  private String movementCreatedBy(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT created_by FROM shopflow.stock_movements WHERE product_id = ?",
        String.class,
        productId);
  }
}
