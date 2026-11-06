package dev.hoangtuan.shopflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.dto.order.CreateOrderRequest;
import dev.hoangtuan.shopflow.dto.payment.CreatePaymentRequest;
import dev.hoangtuan.shopflow.model.payment.SimulatedPaymentResult;
import dev.hoangtuan.shopflow.service.OrderService;
import dev.hoangtuan.shopflow.service.PaymentService;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.application.name=shopflow-checkout-ownership-test"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutOwnershipTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderService orders;
  @Autowired PaymentService payments;
  final JsonMapper json = new JsonMapper();
  long productId;

  @BeforeEach
  void setup() {
    for (String table :
        new String[] {
          "return_request_items",
          "return_requests",
          "delivery_status_history",
          "stock_movements",
          "payments",
          "order_items",
          "orders",
          "inventory_items",
          "products"
        }) {
      jdbc.update("DELETE FROM shopflow." + table);
    }
    jdbc.update("UPDATE shopflow.users SET active = TRUE WHERE email IS NULL");
    productId =
        jdbc.queryForObject(
            "INSERT INTO shopflow.products (name,price,active) VALUES ('Owned filter',100000,TRUE) RETURNING id",
            Long.class);
    jdbc.update(
        "INSERT INTO shopflow.inventory_items (product_id,on_hand_stock,reserved_stock) VALUES (?,10,0)",
        productId);
  }

  @Test
  void guestWithARealCsrfCookieCannotCreateAnOrderOrPayment() throws Exception {
    Client guest = client(new MockHttpSession());
    Map<String, Object> before = state();
    order(guest, body()).andExpect(status().isUnauthorized());
    payment(guest, "unknown", "FAILED").andExpect(status().isUnauthorized());
    assertThat(state()).isEqualTo(before);
  }

  @Test
  void allDemoRolesCreateAndPayOnlyTheirOwnOrdersWithRealCsrfCookies() throws Exception {
    for (String name : new String[] {"customer", "warehouse", "owner"}) {
      Client buyer = login(name);
      var created =
          order(buyer, body())
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.customer.email").value("different-shipping@example.com"))
              .andExpect(jsonPath("$.accountUserId").doesNotExist())
              .andReturn();
      var data = json.readTree(created.getResponse().getContentAsString());
      long id = data.path("id").asLong();
      long userId =
          jdbc.queryForObject("SELECT id FROM shopflow.users WHERE username = ?", Long.class, name);
      assertThat(
              jdbc.queryForObject(
                  "SELECT account_user_id FROM shopflow.orders WHERE id = ?", Long.class, id))
          .isEqualTo(userId);
      assertThat(
              jdbc.queryForObject(
                  "SELECT customer_id FROM shopflow.orders WHERE id = ?", Long.class, id))
          .isNull();
      payment(buyer, data.path("orderRef").asText(), "SUCCESS").andExpect(status().isOk());
      payment(buyer, data.path("orderRef").asText(), "SUCCESS").andExpect(status().isConflict());
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopflow.payments", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void forgedOwnershipOrRoleFieldsAreRejectedBeforeReservation() throws Exception {
    Client buyer = login("customer");
    Map<String, Object> before = state();
    for (String field : new String[] {"accountUserId", "userId", "role"}) {
      Map<String, Object> forged = new LinkedHashMap<>(body());
      forged.put(field, field.equals("role") ? "SHOP_OWNER" : 3);
      order(buyer, forged).andExpect(status().isBadRequest());
      assertThat(state()).isEqualTo(before);
    }
  }

  @Test
  void wrongOwnerUnknownAndLegacyPaymentsHaveTheSame404AndNoSideEffects() throws Exception {
    Client buyer = login("customer");
    var created =
        json.readTree(
            order(buyer, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String ref = created.path("orderRef").asText();
    Map<String, Object> before = state();
    for (String role : new String[] {"warehouse", "owner"}) {
      Client other = login(role);
      String wrong =
          payment(other, ref, "FAILED")
              .andExpect(status().isNotFound())
              .andExpect(jsonPath("$.message").value("Order not found"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      for (String invalid :
          new String[] {"missing", "not-a-uuid", "00000000-0000-0000-0000-000000000000"}) {
        assertThat(
                payment(other, invalid, "FAILED")
                    .andExpect(status().isNotFound())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .isEqualTo(wrong);
      }
      assertThat(state()).isEqualTo(before);
    }
    jdbc.update("UPDATE shopflow.orders SET account_user_id = NULL WHERE order_ref = ?", ref);
    Map<String, Object> legacy =
        jdbc.queryForMap("SELECT * FROM shopflow.orders WHERE order_ref = ?", ref);
    payment(buyer, ref, "FAILED").andExpect(status().isNotFound());
    assertThat(jdbc.queryForMap("SELECT * FROM shopflow.orders WHERE order_ref = ?", ref))
        .isEqualTo(legacy);
    assertThat(state()).isEqualTo(before);
  }

  @Test
  void aResolvedOrderDoesNotRevealItsPaymentStateToAnotherAccount() throws Exception {
    Client buyer = login("customer");
    var created =
        json.readTree(
            order(buyer, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String ref = created.path("orderRef").asText();
    payment(buyer, ref, "SUCCESS").andExpect(status().isOk());
    Map<String, Object> before = state();
    Client other = login("owner");
    String resolved =
        payment(other, ref, "FAILED")
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String missing =
        payment(other, "unknown", "FAILED")
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(resolved).isEqualTo(missing);
    assertThat(state()).isEqualTo(before);
  }

  @Test
  void anOwnedFailureReleasesReservationExactlyOnce() throws Exception {
    Client buyer = login("customer");
    var created =
        json.readTree(
            order(buyer, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String ref = created.path("orderRef").asText();
    payment(buyer, ref, "FAILED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderStatus").value("PAYMENT_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT on_hand_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                productId))
        .isEqualTo(10);
    Map<String, Object> once = state();
    payment(buyer, ref, "FAILED").andExpect(status().isConflict());
    assertThat(state()).isEqualTo(once);
  }

  @Test
  void inactiveOrMissingAccountsCannotWriteThroughDirectServiceCallers() {
    long id =
        jdbc.queryForObject(
            "SELECT id FROM shopflow.users WHERE username = 'customer'", Long.class);
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE id = ?", id);
    var request = json.convertValue(body(), CreateOrderRequest.class);
    Map<String, Object> before = state();
    for (long invalid : new long[] {id, Long.MAX_VALUE}) {
      assertThatThrownBy(() -> orders.createOrder(request, invalid))
          .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      assertThatThrownBy(
              () ->
                  payments.createPayment(
                      "unknown",
                      new CreatePaymentRequest(SimulatedPaymentResult.SUCCESS, null),
                      invalid))
          .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      assertThat(state()).isEqualTo(before);
    }
  }

  @Test
  void aRevokedSessionCannotCreateOrPayAndMissingCsrfStillFails() throws Exception {
    Client buyer = login("customer");
    var created =
        json.readTree(
            order(buyer, body())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            post("/orders")
                .session(buyer.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body())))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE username = 'customer'");
    Map<String, Object> before = state();
    order(buyer, body()).andExpect(status().isUnauthorized());
    Client freshGuest = client(new MockHttpSession());
    payment(freshGuest, created.path("orderRef").asText(), "SUCCESS")
        .andExpect(status().isUnauthorized());
    assertThat(state()).isEqualTo(before);
  }

  private Client login(String name) throws Exception {
    String password = Character.toUpperCase(name.charAt(0)) + name.substring(1) + "@2026";
    var result =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return client((MockHttpSession) result.getRequest().getSession(false));
  }

  private Client client(MockHttpSession session) throws Exception {
    Cookie csrf =
        mvc.perform(get("/auth/session").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return new Client(session, csrf);
  }

  private ResultActions order(Client client, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post("/orders")
            .session(client.session())
            .cookie(client.csrf())
            .header("X-XSRF-TOKEN", client.csrf().getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions payment(Client client, String ref, String result) throws Exception {
    var body =
        result.equals("SUCCESS")
            ? Map.of("result", result)
            : Map.of("result", result, "failureReason", "Simulated failure");
    return mvc.perform(
        post("/orders/{ref}/payments", ref)
            .session(client.session())
            .cookie(client.csrf())
            .header("X-XSRF-TOKEN", client.csrf().getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private Map<String, Object> body() {
    return Map.of(
        "customer",
        Map.of("fullName", "Buyer", "email", "different-shipping@example.com"),
        "shippingAddress",
        Map.of(
            "receiverName",
            "Receiver",
            "phone",
            "0900000000",
            "addressLine",
            "1 Test Street",
            "city",
            "Test City"),
        "paymentMethod",
        "CARD",
        "items",
        java.util.List.of(Map.of("productId", productId, "quantity", 1)));
  }

  private Map<String, Object> state() {
    return jdbc.queryForMap(
        """
        SELECT (SELECT COUNT(*) FROM shopflow.orders) AS orders,
          (SELECT COUNT(*) FROM shopflow.payments) AS payments,
          (SELECT COUNT(*) FROM shopflow.stock_movements) AS movements,
          (SELECT SUM(reserved_stock) FROM shopflow.inventory_items) AS reserved
        """);
  }

  private record Client(MockHttpSession session, Cookie csrf) {}
}
