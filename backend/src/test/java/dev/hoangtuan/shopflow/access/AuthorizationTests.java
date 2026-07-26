package dev.hoangtuan.shopflow.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Ma trận quyền trong {@code docs/api-authorization-spec.md} được kiểm chứng ở đây. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationTests {

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void buildMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .defaultRequest(get("/").with(csrf()))
            .build();
  }

  private static Stream<MockHttpServletRequestBuilder> protectedEndpoints() {
    return Stream.of(
        get("/inventory"),
        post("/inventory/1/adjustments").contentType(MediaType.APPLICATION_JSON).content("{}"),
        put("/inventory/1/threshold").contentType(MediaType.APPLICATION_JSON).content("{}"),
        post("/receivings").contentType(MediaType.APPLICATION_JSON).content("{}"),
        get("/deliveries"),
        patch("/orders/1/delivery").contentType(MediaType.APPLICATION_JSON).content("{}"),
        get("/returns"),
        get("/returns/orders"),
        post("/returns").contentType(MediaType.APPLICATION_JSON).content("{}"),
        patch("/returns/1").contentType(MediaType.APPLICATION_JSON).content("{}"));
  }

  private static Stream<MockHttpServletRequestBuilder> shopOwnerOnlyEndpoints() {
    return Stream.of(
        get("/returns/orders"),
        post("/returns").contentType(MediaType.APPLICATION_JSON).content("{}"));
  }

  @ParameterizedTest
  @MethodSource("protectedEndpoints")
  void refusesEveryOperationalEndpointToAnonymousCallers(MockHttpServletRequestBuilder request)
      throws Exception {
    mockMvc.perform(request).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @MethodSource("protectedEndpoints")
  void refusesEveryOperationalEndpointToCustomers(MockHttpServletRequestBuilder request)
      throws Exception {
    mockMvc
        .perform(request.with(user("shopper").roles("CUSTOMER")))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @MethodSource("shopOwnerOnlyEndpoints")
  void keepsCommercialReturnDecisionsAwayFromTheWarehouse(MockHttpServletRequestBuilder request)
      throws Exception {
    mockMvc
        .perform(request.with(user("keeper").roles("WAREHOUSE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void letsTheWarehouseRestockButNotApproveOrRejectAReturn() throws Exception {
    long returnId = insertDeliveredOrderWithReturn();

    for (String decision : new String[] {"APPROVED", "REJECTED"}) {
      mockMvc
          .perform(
              patch("/returns/{id}", returnId)
                  .with(user("keeper").roles("WAREHOUSE"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"toStatus\":\"%s\",\"restockable\":true}".formatted(decision)))
          .andExpect(status().isForbidden());
      assertThat(returnStatus(returnId)).isEqualTo("REQUESTED");
    }

    mockMvc
        .perform(
            patch("/returns/{id}", returnId)
                .with(user("boss").roles("SHOP_OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"APPROVED\",\"restockable\":true}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/returns/{id}", returnId)
                .with(user("keeper").roles("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"RESTOCKED\"}"))
        .andExpect(status().isOk());

    assertThat(returnStatus(returnId)).isEqualTo("RESTOCKED");
  }

  @Test
  void leavesDataUntouchedWhenAuthorizationRejectsTheRequest() throws Exception {
    long productId = insertProduct("Keyboard", "1990000");
    insertInventory(productId, 10, 0);

    mockMvc
        .perform(
            post("/inventory/{id}/adjustments", productId)
                .with(user("shopper").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-5,\"reason\":\"theft\"}"))
        .andExpect(status().isForbidden());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shopflow.stock_movements", Integer.class))
        .isZero();
  }

  @Test
  void rejectsStateChangingRequestsThatCarryNoCsrfToken() throws Exception {
    MockMvc withoutCsrfDefault =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    withoutCsrfDefault
        .perform(
            post("/receivings")
                .with(user("keeper").roles("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void keepsGuestShoppingAndHealthChecksOpen() throws Exception {
    mockMvc.perform(get("/products")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/auth/session")).andExpect(status().isOk());
  }

  @Test
  void letsBothOperationalRolesReachSharedWarehouseScreens() throws Exception {
    for (String role : new String[] {"WAREHOUSE", "SHOP_OWNER"}) {
      mockMvc.perform(get("/inventory").with(user("u").roles(role))).andExpect(status().isOk());
      mockMvc.perform(get("/deliveries").with(user("u").roles(role))).andExpect(status().isOk());
      mockMvc.perform(get("/returns").with(user("u").roles(role))).andExpect(status().isOk());
    }
  }

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.stock_movements");
    jdbcTemplate.update("DELETE FROM shopflow.return_request_items");
    jdbcTemplate.update("DELETE FROM shopflow.return_requests");
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  private long insertDeliveredOrderWithReturn() {
    long productId = insertProduct("Mouse", "590000");
    insertInventory(productId, 5, 0);
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          status, delivery_status, payment_method, total_amount
        ) VALUES ('Guest', 'Guest', '0900000000', '1 Main', 'Hanoi',
                  'PAID', 'DELIVERED', 'CARD', ?)
        """,
        new BigDecimal("590000"));
    long orderId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.order_items (order_id, product_id, product_name, unit_price, quantity)
        VALUES (?, ?, 'Mouse', ?, 2)
        """,
        orderId,
        productId,
        new BigDecimal("295000"));
    long orderItemId =
        jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.order_items", Long.class);
    jdbcTemplate.update(
        "INSERT INTO shopflow.return_requests (order_id, status, restockable) VALUES (?, ?, ?)",
        orderId,
        "REQUESTED",
        false);
    long returnId =
        jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.return_requests", Long.class);
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.return_request_items (return_request_id, order_item_id, quantity)
        VALUES (?, ?, 1)
        """,
        returnId,
        orderItemId);
    return returnId;
  }

  private String returnStatus(long returnId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM shopflow.return_requests WHERE id = ?", String.class, returnId);
  }

  private long insertProduct(String name, String price) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, TRUE)", name, price);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHand, int reserved) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items (product_id, on_hand_stock, reserved_stock)"
            + " VALUES (?, ?, ?)",
        productId,
        onHand,
        reserved);
  }
}
