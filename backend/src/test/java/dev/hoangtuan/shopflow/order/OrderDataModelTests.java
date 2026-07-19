package dev.hoangtuan.shopflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class OrderDataModelTests {

  @Autowired private OrderRepository orderRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.order_items");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
  }

  @Test
  void persistsOrderAndSnapshotsAgainstExistingSchema() {
    long productId = insertProduct("Coffee");
    Order order =
        new Order(
            new CustomerSnapshot("Nguyen Van A", "a@example.com", "0900000000"),
            new ShippingAddress(
                "Nguyen Van A", "0900000000", "1 Main Street", "District 1", "HCMC"),
            PaymentMethod.CARD,
            new BigDecimal("2190000"));
    OrderItem item = new OrderItem(productId, "Coffee", new BigDecimal("2190000"), 1);
    order.addItem(item);

    Order saved = orderRepository.saveAndFlush(order);

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.NONE);
    assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.getItems())
        .singleElement()
        .satisfies(
            savedItem -> {
              assertThat(savedItem.getOrder().getId()).isEqualTo(saved.getId());
              assertThat(savedItem.getProductId()).isEqualTo(productId);
              assertThat(savedItem.getProductName()).isEqualTo("Coffee");
              assertThat(savedItem.getUnitPrice()).isEqualByComparingTo("2190000");
              assertThat(savedItem.getQuantity()).isEqualTo(1);
            });
  }

  @Test
  void mapsOptionalDistrictAndCustomerFields() {
    long productId = insertProduct("Free sample");
    Order order =
        new Order(
            new CustomerSnapshot("Guest", null, null),
            new ShippingAddress("Guest", "0911111111", "2 Main Street", null, "Hanoi"),
            PaymentMethod.CARD,
            new BigDecimal("0"));
    order.addItem(new OrderItem(productId, "Free sample", BigDecimal.ZERO, 1));

    Order saved = orderRepository.saveAndFlush(order);

    assertThat(saved.getCustomer().getEmail()).isNull();
    assertThat(saved.getCustomer().getPhone()).isNull();
    assertThat(saved.getShippingAddress().getDistrict()).isNull();
  }

  private long insertProduct(String name) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, price, active) VALUES (?, ?, ?)", name, 0, true);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }
}
