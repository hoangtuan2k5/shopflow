package dev.hoangtuan.shopflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
class DashboardControllerTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void clean() {
    jdbc.execute("TRUNCATE shopflow.products, shopflow.orders RESTART IDENTITY CASCADE");
  }

  private MockHttpServletRequestBuilder dashboard() {
    return get("/dashboard").with(csrf()).with(user("owner").roles("SHOP_OWNER"));
  }

  @Test
  void enforcesOwnerAccess() throws Exception {
    mvc.perform(get("/dashboard").with(csrf())).andExpect(status().isUnauthorized());
    for (String role : new String[] {"CUSTOMER", "WAREHOUSE"}) {
      mvc.perform(get("/dashboard").with(csrf()).with(user("staff").roles(role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(dashboard()).andExpect(status().isOk());
  }

  @Test
  void aggregatesPaidOrdersOnceAndUsesPurchaseSnapshots() throws Exception {
    long product = product("Renamed product", true, 5);
    jdbc.update(
        "INSERT INTO shopflow.inventory_items(product_id,on_hand_stock,reserved_stock) VALUES (?,10,5)",
        product);
    long inactive = product("Inactive", false, 5);
    jdbc.update(
        "INSERT INTO shopflow.inventory_items(product_id,on_hand_stock) VALUES (?,0)", inactive);
    long paid = order("PAID", "DELIVERED", "300", "2026-10-01T17:00:00Z");
    item(paid, product, "Original filter", "100", 2);
    item(paid, inactive, "Old inactive filter", "100", 1);
    payment(paid, "FAILED", null);
    payment(paid, "SUCCESS", "2026-10-01T17:30:00Z");
    payment(paid, "SUCCESS", "2026-10-02T01:00:00Z");
    long pending = order("PENDING_PAYMENT", "NONE", "999", "2026-10-02T02:00:00Z");
    payment(pending, "PENDING", null);
    long failed = order("PAYMENT_FAILED", "NONE", "999", "2026-10-02T03:00:00Z");
    payment(failed, "FAILED", null);
    order("PAID", "SHIPPED", "400", "2025-01-01T00:00:00Z");
    jdbc.update(
        "INSERT INTO shopflow.return_requests(order_id,status,restockable) VALUES (?,'RESTOCKED',true)",
        paid);
    mvc.perform(dashboard().param("from", "2026-10-02").param("to", "2026-10-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidAmount").value("300"))
        .andExpect(jsonPath("$.orderCount").value(3))
        .andExpect(jsonPath("$.processingOrders").value(1))
        .andExpect(jsonPath("$.lowStockProducts").value(1))
        .andExpect(jsonPath("$.series[0].date").value("2026-10-02"))
        .andExpect(jsonPath("$.series[0].paidAmount").value("300"))
        .andExpect(jsonPath("$.series[1].paidAmount").value("0"))
        .andExpect(jsonPath("$.statuses[1].count").value(1))
        .andExpect(jsonPath("$.topProducts[0].productName").value("Original filter"))
        .andExpect(jsonPath("$.topProducts[0].quantity").value(2))
        .andExpect(jsonPath("$.topProducts[0].paidAmount").value("200"));
    mvc.perform(dashboard().param("from", "2026-10-01").param("to", "2026-10-01"))
        .andExpect(jsonPath("$.paidAmount").value("0"))
        .andExpect(jsonPath("$.orderCount").value(0));
    mvc.perform(
            dashboard()
                .param("from", "2026-10-01")
                .param("to", "2026-10-03")
                .param("groupBy", "month"))
        .andExpect(jsonPath("$.series.length()").value(1))
        .andExpect(jsonPath("$.series[0].date").value("2026-10-01"))
        .andExpect(jsonPath("$.series[0].paidAmount").value("300"));
  }

  @Test
  void filtersAfterDeduplicatingTheFirstSuccessfulPayment() throws Exception {
    long id = order("PAID", "DELIVERED", "100", "2026-09-30T00:00:00Z");
    payment(id, "SUCCESS", "2026-09-30T16:59:59Z");
    payment(id, "SUCCESS", "2026-09-30T17:00:00Z");
    mvc.perform(dashboard().param("from", "2026-10-01").param("to", "2026-10-01"))
        .andExpect(jsonPath("$.paidAmount").value("0"));
    long second = order("PAID", "DELIVERED", "200", "2026-09-30T17:00:00Z");
    payment(second, "SUCCESS", "2026-09-30T17:00:00Z");
    long third = order("PAID", "DELIVERED", "400", "2026-10-01T17:00:00Z");
    payment(third, "SUCCESS", "2026-10-01T17:00:00Z");
    mvc.perform(dashboard().param("from", "2026-10-01").param("to", "2026-10-01"))
        .andExpect(jsonPath("$.paidAmount").value("200"))
        .andExpect(jsonPath("$.orderCount").value(1));
  }

  @Test
  void validatesRangesAndDefaultsToVietnameseCalendar() throws Exception {
    for (String[] pair :
        new String[][] {
          {"2026-10-02", "2026-10-01"},
          {"2025-01-01", "2026-01-02"},
          {"bad", "2026-10-01"},
          {"2026-02-30", "2026-03-01"}
        }) {
      mvc.perform(dashboard().param("from", pair[0]).param("to", pair[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400));
    }
    mvc.perform(dashboard().param("from", "2026-10-01")).andExpect(status().isBadRequest());
    mvc.perform(dashboard().param("groupBy", "week")).andExpect(status().isBadRequest());
    mvc.perform(dashboard().param("from", "2024-01-01").param("to", "2024-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.length()").value(366));
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    mvc.perform(dashboard())
        .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
        .andExpect(jsonPath("$.to").value(today.toString()))
        .andExpect(jsonPath("$.series.length()").value(30));
  }

  @Test
  void keepsLargeMoneyExactAndLimitsTopProductsDeterministically() throws Exception {
    for (int i = 0; i < 6; i++) {
      long product = product("Filter " + i, true, null);
      long order = order("PAID", "NONE", "9000000000", "2026-10-01T00:00:00Z");
      item(order, product, "Snapshot " + i, "9000000000", 1);
      payment(order, "SUCCESS", "2026-10-01T00:00:00Z");
    }
    mvc.perform(dashboard().param("from", "2026-10-01").param("to", "2026-10-01"))
        .andExpect(jsonPath("$.paidAmount").value("54000000000"))
        .andExpect(jsonPath("$.topProducts.length()").value(5))
        .andExpect(jsonPath("$.topProducts[0].productId").value(1));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM shopflow.orders", Long.class))
        .isEqualTo(6);
  }

  @Test
  void handlesEmptyMonthsAndMissingInventoryWithoutLosingCalendarBoundaries() throws Exception {
    product("Missing inventory", true, 0);
    long id = order("PAID", "DELIVERED", "500", "2026-10-31T17:00:00Z");
    payment(id, "SUCCESS", "2026-10-31T17:00:00Z");
    mvc.perform(
            dashboard()
                .param("from", "2026-10-30")
                .param("to", "2026-12-01")
                .param("groupBy", "month"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.length()").value(3))
        .andExpect(jsonPath("$.series[0].paidAmount").value("0"))
        .andExpect(jsonPath("$.series[1].date").value("2026-11-01"))
        .andExpect(jsonPath("$.series[1].paidAmount").value("500"))
        .andExpect(jsonPath("$.series[2].paidAmount").value("0"))
        .andExpect(jsonPath("$.lowStockProducts").value(1));
  }

  private long product(String name, boolean active, Integer threshold) {
    return jdbc.queryForObject(
        "INSERT INTO shopflow.products(name,price,active,low_stock_threshold) VALUES (?,999999,?,?) RETURNING id",
        Long.class,
        name,
        active,
        threshold);
  }

  private long order(String status, String delivery, String amount, String at) {
    return jdbc.queryForObject(
        """
        INSERT INTO shopflow.orders(customer_name,receiver_name,receiver_phone,address_line,city,status,delivery_status,payment_method,total_amount,created_at)
        VALUES ('Customer','Receiver','0000000000','Street','City',?,?,'CARD',?,?) RETURNING id
        """,
        Long.class,
        status,
        delivery,
        new BigDecimal(amount),
        Timestamp.from(Instant.parse(at)));
  }

  private void payment(long order, String status, String paidAt) {
    jdbc.update(
        "INSERT INTO shopflow.payments(order_id,method,status,amount,paid_at) VALUES (?,'CARD',?,100,?)",
        order,
        status,
        paidAt == null ? null : Timestamp.from(Instant.parse(paidAt)));
  }

  private void item(long order, long product, String name, String price, int quantity) {
    jdbc.update(
        "INSERT INTO shopflow.order_items(order_id,product_id,product_name,unit_price,quantity) VALUES (?,?,?,?,?)",
        order,
        product,
        name,
        new BigDecimal(price),
        quantity);
  }
}
