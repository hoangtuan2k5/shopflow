package dev.hoangtuan.shopflow.customerreturn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ReturnControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.return_request_items");
    jdbcTemplate.update("DELETE FROM shopflow.return_requests");
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.payments");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void createsReturnForDeliveredOrderWithoutTouchingInventory() throws Exception {
    long productId = insertProduct("Keyboard");
    insertInventory(productId, 5, 0);
    long orderId = insertOrder("PAID", "DELIVERED");
    long orderItemId = insertOrderItem(orderId, productId, "Keyboard", 3);

    createReturn(orderId, "  Damaged box  ", orderItemId, 2)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.status").value("REQUESTED"))
        .andExpect(jsonPath("$.reason").value("Damaged box"))
        .andExpect(jsonPath("$.restockable").value(false))
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].orderItemId").value(orderItemId))
        .andExpect(jsonPath("$.items[0].productId").value(productId))
        .andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
        .andExpect(jsonPath("$.items[0].quantity").value(2));

    assertThat(onHandStock(productId)).isEqualTo(5);
    assertThat(movementCount()).isZero();

    mockMvc
        .perform(get("/returns"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("REQUESTED"));
    mockMvc
        .perform(get("/returns/orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].orderId").value(orderId))
        .andExpect(jsonPath("$[0].items[0].quantity").value(3))
        .andExpect(jsonPath("$[0].items[0].returnedQuantity").value(2))
        .andExpect(jsonPath("$[0].items[0].returnableQuantity").value(1));
  }

  @Test
  void rejectsCreationForUndeliveredMissingOrForeignOrders() throws Exception {
    long productId = insertProduct("Monitor");
    insertInventory(productId, 5, 0);
    long shippedOrderId = insertOrder("PAID", "SHIPPED");
    long shippedItemId = insertOrderItem(shippedOrderId, productId, "Monitor", 2);
    long deliveredOrderId = insertOrder("PAID", "DELIVERED");
    insertOrderItem(deliveredOrderId, productId, "Monitor", 2);

    createReturn(shippedOrderId, null, shippedItemId, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Order is not delivered"));
    createReturn(Long.MAX_VALUE, null, shippedItemId, 1)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Order not found"));
    createReturn(deliveredOrderId, null, shippedItemId, 1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.items").isString());

    assertThat(returnCount()).isZero();
    assertThat(movementCount()).isZero();
  }

  @Test
  void rejectsInvalidCreateBodiesWithoutSideEffects() throws Exception {
    long productId = insertProduct("Mouse");
    long orderId = insertOrder("PAID", "DELIVERED");
    long orderItemId = insertOrderItem(orderId, productId, "Mouse", 3);

    rawCreate("{\"orderId\":%d,\"items\":[]}".formatted(orderId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.items").isString());
    rawCreate(
            "{\"orderId\":%d,\"items\":[{\"orderItemId\":%d,\"quantity\":0}]}"
                .formatted(orderId, orderItemId))
        .andExpect(status().isBadRequest());
    rawCreate(
            "{\"orderId\":%d,\"items\":[{\"orderItemId\":%d,\"quantity\":1.5}]}"
                .formatted(orderId, orderItemId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));
    rawCreate(
            ("{\"orderId\":%d,\"reason\":\"  \","
                    + "\"items\":[{\"orderItemId\":%d,\"quantity\":1}]}")
                .formatted(orderId, orderItemId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.reason").isString());
    rawCreate(
            ("{\"orderId\":%d,\"items\":[{\"orderItemId\":%d,\"quantity\":1},"
                    + "{\"orderItemId\":%d,\"quantity\":1}]}")
                .formatted(orderId, orderItemId, orderItemId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.items").isString());

    assertThat(returnCount()).isZero();
  }

  @Test
  void enforcesReturnableQuantityAcrossRequestsExcludingRejected() throws Exception {
    long productId = insertProduct("Speaker");
    long orderId = insertOrder("PAID", "DELIVERED");
    long orderItemId = insertOrderItem(orderId, productId, "Speaker", 3);

    createReturn(orderId, null, orderItemId, 2).andExpect(status().isCreated());
    createReturn(orderId, null, orderItemId, 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Return exceeds purchased quantity"));

    createReturn(orderId, null, orderItemId, 1).andExpect(status().isCreated());
    long secondReturnId = latestReturnId();
    createReturn(orderId, null, orderItemId, 1).andExpect(status().isConflict());

    update(secondReturnId, "{\"toStatus\":\"REJECTED\"}").andExpect(status().isOk());
    createReturn(orderId, null, orderItemId, 1).andExpect(status().isCreated());
    long thirdReturnId = latestReturnId();

    long firstReturnId = firstReturnId();
    update(firstReturnId, "{\"toStatus\":\"APPROVED\",\"restockable\":true}")
        .andExpect(status().isOk());
    update(firstReturnId, "{\"toStatus\":\"RESTOCKED\"}").andExpect(status().isOk());
    createReturn(orderId, null, orderItemId, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Return exceeds purchased quantity"));

    update(thirdReturnId, "{\"toStatus\":\"REJECTED\"}").andExpect(status().isOk());
    createReturn(orderId, null, orderItemId, 1).andExpect(status().isCreated());

    assertThat(returnCount()).isEqualTo(4);
    assertThat(movementCount()).isEqualTo(1);
  }

  @Test
  void approvesAndRestocksEveryItemAtomically() throws Exception {
    long keyboardId = insertProduct("Keyboard");
    long mouseId = insertProduct("Mouse");
    insertInventory(keyboardId, 5, 1);
    long orderId = insertOrder("PAID", "DELIVERED");
    long keyboardItemId = insertOrderItem(orderId, keyboardId, "Keyboard", 2);
    long mouseItemId = insertOrderItem(orderId, mouseId, "Mouse", 1);

    rawCreate(
            ("{\"orderId\":%d,\"items\":[{\"orderItemId\":%d,\"quantity\":2},"
                    + "{\"orderItemId\":%d,\"quantity\":1}]}")
                .formatted(orderId, keyboardItemId, mouseItemId))
        .andExpect(status().isCreated());
    long returnId = latestReturnId();

    update(returnId, "{\"toStatus\":\"APPROVED\",\"restockable\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.restockable").value(true));
    assertThat(onHandStock(keyboardId)).isEqualTo(5);
    assertThat(movementCount()).isZero();

    update(returnId, "{\"toStatus\":\"RESTOCKED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESTOCKED"));

    assertThat(onHandStock(keyboardId)).isEqualTo(7);
    assertThat(onHandStock(mouseId)).isEqualTo(1);
    assertThat(movementCount()).isEqualTo(2);
    assertThat(movementQuantity(keyboardId)).isEqualTo(2);
    assertThat(movementQuantity(mouseId)).isEqualTo(1);
    assertThat(movementType(keyboardId)).isEqualTo("RETURN_RESTOCK");
    assertThat(movementReferenceType(keyboardId)).isEqualTo("RETURN");
    assertThat(movementReferenceId(keyboardId)).isEqualTo(returnId);
    assertThat(movementCreatedBy(keyboardId)).isNull();

    update(returnId, "{\"toStatus\":\"RESTOCKED\"}").andExpect(status().isConflict());
    assertThat(onHandStock(keyboardId)).isEqualTo(7);
    assertThat(movementCount()).isEqualTo(2);
  }

  @Test
  void rejectsInvalidTransitionsAndNonRestockableRestocks() throws Exception {
    long productId = insertProduct("Webcam");
    insertInventory(productId, 4, 0);
    long orderId = insertOrder("PAID", "DELIVERED");
    long orderItemId = insertOrderItem(orderId, productId, "Webcam", 2);

    createReturn(orderId, null, orderItemId, 1).andExpect(status().isCreated());
    long rejectedId = latestReturnId();
    update(rejectedId, "{\"toStatus\":\"REJECTED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    update(rejectedId, "{\"toStatus\":\"APPROVED\",\"restockable\":true}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Unsupported return transition"));

    createReturn(orderId, null, orderItemId, 1).andExpect(status().isCreated());
    long unrestockableId = latestReturnId();
    update(unrestockableId, "{\"toStatus\":\"REQUESTED\"}").andExpect(status().isConflict());
    update(unrestockableId, "{\"toStatus\":\"APPROVED\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.restockable").isString());
    update(unrestockableId, "{\"toStatus\":\"REJECTED\",\"restockable\":true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.restockable").isString());
    update(unrestockableId, "{\"toStatus\":\"APPROVED\",\"restockable\":false}")
        .andExpect(status().isOk());
    update(unrestockableId, "{\"toStatus\":\"RESTOCKED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Return is not restockable"));

    update(Long.MAX_VALUE, "{\"toStatus\":\"REJECTED\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Return not found"));
    update(unrestockableId, "{\"toStatus\":\"SOMETHING\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));

    assertThat(onHandStock(productId)).isEqualTo(4);
    assertThat(movementCount()).isZero();
  }

  private ResultActions createReturn(long orderId, String reason, long orderItemId, int quantity)
      throws Exception {
    String body =
        "{\"orderId\":%d%s,\"items\":[{\"orderItemId\":%d,\"quantity\":%d}]}"
            .formatted(
                orderId,
                reason == null ? "" : ",\"reason\":\"%s\"".formatted(reason),
                orderItemId,
                quantity);
    return rawCreate(body);
  }

  private ResultActions rawCreate(String body) throws Exception {
    return mockMvc.perform(post("/returns").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions update(long returnId, String body) throws Exception {
    return mockMvc.perform(
        patch("/returns/{returnId}", returnId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private long insertProduct(String name) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, TRUE)",
        name,
        new BigDecimal("1000"));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHandStock, int reservedStock) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHandStock,
        reservedStock);
  }

  private long insertOrder(String status, String deliveryStatus) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, delivery_status, payment_method, total_amount
        ) VALUES ('Guest', 'Receiver', '0900000000', '1 Main Street', 'Hanoi', ?, ?, 'CARD', 1000)
        """,
        status,
        deliveryStatus);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }

  private long insertOrderItem(long orderId, long productId, String productName, int quantity) {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, ?, 1000, ?)
        """,
        orderId,
        productId,
        productName,
        quantity);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.order_items", Long.class);
  }

  private long latestReturnId() {
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.return_requests", Long.class);
  }

  private long firstReturnId() {
    return jdbcTemplate.queryForObject("SELECT MIN(id) FROM shopflow.return_requests", Long.class);
  }

  private int returnCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.return_requests", Integer.class);
  }

  private int onHandStock(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
        Integer.class,
        productId);
  }

  private int movementCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.stock_movements", Integer.class);
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

  private String movementCreatedBy(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT created_by FROM shopflow.stock_movements WHERE product_id = ?",
        String.class,
        productId);
  }
}
