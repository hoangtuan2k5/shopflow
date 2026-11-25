package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hoangtuan.shopflow.repository.jdbc.DeliveryRepository;
import dev.hoangtuan.shopflow.repository.jdbc.ReturnRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class BatchReadTests {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  private JdbcTemplate db;
  private DeliveryService deliveries;
  private ReturnService returns;
  private final AtomicInteger selects = new AtomicInteger();

  @BeforeEach
  void setup() {
    var raw =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    db = new JdbcTemplate(raw);
    db.execute("DROP SCHEMA IF EXISTS shopflow CASCADE");
    db.execute("CREATE SCHEMA shopflow");
    db.execute(
        "CREATE TABLE shopflow.orders (id bigint primary key, status text, delivery_status text, receiver_name text, city text, total_amount numeric, created_at timestamptz)");
    db.execute(
        "CREATE TABLE shopflow.order_items (id bigint primary key, order_id bigint, product_id bigint, product_name text, quantity integer)");
    db.execute(
        "CREATE TABLE shopflow.delivery_status_history (id bigint primary key, order_id bigint, from_status text, to_status text, changed_at timestamptz, changed_by text)");
    db.execute(
        "CREATE TABLE shopflow.return_requests (id bigint primary key, order_id bigint, status text, reason text, restockable boolean, created_at timestamptz, updated_at timestamptz)");
    db.execute(
        "CREATE TABLE shopflow.return_request_items (id bigint primary key, return_request_id bigint, order_item_id bigint, quantity integer)");
    DataSource counted =
        (DataSource)
            Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                  try {
                    Object result = method.invoke(raw, args);
                    if (result instanceof Connection connection) {
                      return Proxy.newProxyInstance(
                          Connection.class.getClassLoader(),
                          new Class<?>[] {Connection.class},
                          (p, m, a) -> {
                            if ((m.getName().equals("prepareStatement")
                                    || m.getName().equals("prepareCall"))
                                && a != null
                                && a[0] instanceof String sql
                                && sql.stripLeading().toUpperCase().startsWith("SELECT"))
                              selects.incrementAndGet();
                            try {
                              Object value = m.invoke(connection, a);
                              if (m.getName().equals("createStatement")
                                  && value instanceof java.sql.Statement statement) {
                                return Proxy.newProxyInstance(
                                    java.sql.Statement.class.getClassLoader(),
                                    new Class<?>[] {java.sql.Statement.class},
                                    (sp, sm, sa) -> {
                                      if (sm.getName().equals("executeQuery")
                                          && sa != null
                                          && sa[0] instanceof String sql
                                          && sql.stripLeading().toUpperCase().startsWith("SELECT"))
                                        selects.incrementAndGet();
                                      try {
                                        return sm.invoke(statement, sa);
                                      } catch (InvocationTargetException e) {
                                        throw e.getCause();
                                      }
                                    });
                              }
                              return value;
                            } catch (InvocationTargetException e) {
                              throw e.getCause();
                            }
                          });
                    }
                    return result;
                  } catch (InvocationTargetException e) {
                    throw e.getCause();
                  }
                });
    var jdbc = new JdbcTemplate(counted);
    deliveries = new DeliveryService(new DeliveryRepository(jdbc));
    returns = new ReturnService(new ReturnRepository(jdbc));
  }

  private void seed(int n) {
    for (int i = 1; i <= n; i++) {
      db.update(
          "INSERT INTO shopflow.orders VALUES (?, 'PAID', 'DELIVERED', ?, 'Hanoi', 100, now() + (? * interval '1 second'))",
          i,
          "Receiver " + i,
          i);
      db.update("INSERT INTO shopflow.order_items VALUES (?, ?, 7, 'Same product', 3)", i, i);
      db.update(
          "INSERT INTO shopflow.delivery_status_history VALUES (?, ?, 'NONE', 'PREPARING', now(), NULL)",
          i * 2,
          i);
      db.update(
          "INSERT INTO shopflow.delivery_status_history VALUES (?, ?, 'PREPARING', 'SHIPPED', now() + interval '1 second', NULL)",
          i * 2 + 1,
          i);
      db.update(
          "INSERT INTO shopflow.return_requests VALUES (?, ?, 'APPROVED', NULL, true, now() + (? * interval '1 second'), now())",
          i,
          i,
          i);
      db.update("INSERT INTO shopflow.return_request_items VALUES (?, ?, ?, 2)", i, i, i);
    }
  }

  @Test
  void boundedQueriesAndCorrectPayloadForOneAndTwentyParents() throws Exception {
    seed(1);
    check(1);
    db.execute(
        "TRUNCATE shopflow.return_request_items, shopflow.return_requests, shopflow.delivery_status_history, shopflow.order_items, shopflow.orders");
    seed(20);
    check(20);
  }

  @Test
  void emptyParentsSkipChildQueries() {
    selects.set(0);
    assertThat(deliveries.getDeliveries()).isEmpty();
    assertThat(selects.get()).isEqualTo(1);
    selects.set(0);
    assertThat(returns.list()).isEmpty();
    assertThat(selects.get()).isEqualTo(1);
    selects.set(0);
    assertThat(returns.returnableOrders()).isEmpty();
    assertThat(selects.get()).isEqualTo(1);
  }

  @Test
  void preservesGroupingOrderStatusesAndEmptyChildren() {
    seed(2);
    db.update("INSERT INTO shopflow.order_items VALUES (101, 1, 7, 'Same product', 4)");
    db.update("INSERT INTO shopflow.order_items VALUES (102, 1, 7, 'Other snapshot', 5)");
    db.update(
        "INSERT INTO shopflow.return_requests VALUES (101, 1, 'REQUESTED', NULL, false, now(), now())");
    db.update(
        "INSERT INTO shopflow.return_requests VALUES (102, 1, 'RESTOCKED', NULL, true, now(), now())");
    db.update(
        "INSERT INTO shopflow.return_requests VALUES (103, 1, 'REJECTED', NULL, false, now(), now())");
    db.update("INSERT INTO shopflow.return_request_items VALUES (101, 101, 1, 1)");
    db.update("INSERT INTO shopflow.return_request_items VALUES (102, 102, 1, 1)");
    db.update("INSERT INTO shopflow.return_request_items VALUES (103, 103, 1, 99)");
    db.update(
        "INSERT INTO shopflow.orders VALUES (3, 'PAID', 'DELIVERED', 'Empty', 'Hanoi', 100, now() + interval '3 seconds')");
    db.update(
        "INSERT INTO shopflow.return_requests VALUES (104, 3, 'REQUESTED', NULL, false, now(), now())");
    var delivery = deliveries.getDeliveries();
    assertThat(delivery).extracting(d -> d.orderId()).containsExactly(1L, 2L, 3L);
    assertThat(delivery.getFirst().items()).hasSize(2);
    assertThat(delivery.getFirst().items())
        .filteredOn(i -> i.productName().equals("Same product"))
        .singleElement()
        .satisfies(i -> assertThat(i.quantity()).isEqualTo(7));
    assertThat(delivery.getFirst().history())
        .extracting(h -> h.toStatus().name())
        .containsExactly("PREPARING", "SHIPPED");
    assertThat(delivery.getLast().items()).isEmpty();
    assertThat(delivery.getLast().history()).isEmpty();
    var orders = returns.returnableOrders();
    assertThat(orders).extracting(o -> o.orderId()).containsExactly(3L, 2L, 1L);
    assertThat(orders.getFirst().items()).isEmpty();
    assertThat(orders.getLast().items())
        .extracting(i -> i.orderItemId())
        .containsExactly(1L, 101L, 102L);
    assertThat(orders.getLast().items().getFirst().returnedQuantity()).isEqualTo(4);
    var requests = returns.list();
    assertThat(requests)
        .filteredOn(r -> r.id() == 104L)
        .singleElement()
        .satisfies(r -> assertThat(r.items()).isEmpty());
    assertThat(requests)
        .filteredOn(r -> r.id() == 1L)
        .singleElement()
        .satisfies(r -> assertThat(r.items()).hasSize(1));
  }

  private void check(int n) throws Exception {
    selects.set(0);
    var delivery = deliveries.getDeliveries();
    assertThat(selects.get()).isEqualTo(3);
    assertThat(delivery).hasSize(n);
    assertThat(delivery.getFirst().items()).hasSize(1);
    assertThat(delivery.getFirst().history()).hasSize(2);
    selects.set(0);
    var requests = returns.list();
    assertThat(selects.get()).isEqualTo(2);
    assertThat(requests).hasSize(n);
    assertThat(requests.getFirst().items()).hasSize(1);
    selects.set(0);
    var orders = returns.returnableOrders();
    assertThat(selects.get()).isEqualTo(2);
    assertThat(orders).hasSize(n);
    assertThat(orders.getFirst().items().getFirst().returnedQuantity()).isEqualTo(2);

    var deliveryConstructor =
        dev.hoangtuan.shopflow.controller.DeliveryController.class.getDeclaredConstructor(
            DeliveryService.class);
    var returnConstructor =
        dev.hoangtuan.shopflow.controller.ReturnController.class.getDeclaredConstructor(
            ReturnService.class);
    deliveryConstructor.setAccessible(true);
    returnConstructor.setAccessible(true);
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                deliveryConstructor.newInstance(deliveries), returnConstructor.newInstance(returns))
            .build();
    for (var entry :
        java.util.Map.of("/deliveries", 3, "/returns", 2, "/returns/orders", 2).entrySet()) {
      selects.set(0);
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  entry.getKey()))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                      "$.length()")
                  .value(n));
      assertThat(selects.get()).as(entry.getKey()).isEqualTo(entry.getValue());
    }
  }
}
