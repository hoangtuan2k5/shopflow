package dev.hoangtuan.shopflow.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", schema = "shopflow")
class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "customer_id")
  private Long customerId;

  @Embedded private CustomerSnapshot customer;

  @Embedded private ShippingAddress shippingAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private OrderStatus status = OrderStatus.PENDING_PAYMENT;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_status", nullable = false, length = 30)
  private DeliveryStatus deliveryStatus = DeliveryStatus.NONE;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private PaymentMethod paymentMethod;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private final List<OrderItem> items = new ArrayList<>();

  protected Order() {}

  Order(
      CustomerSnapshot customer,
      ShippingAddress shippingAddress,
      PaymentMethod paymentMethod,
      BigDecimal totalAmount) {
    this.customer = customer;
    this.shippingAddress = shippingAddress;
    this.paymentMethod = paymentMethod;
    this.totalAmount = totalAmount;
  }

  @jakarta.persistence.PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @jakarta.persistence.PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  void addItem(OrderItem item) {
    item.attachTo(this);
    items.add(item);
  }

  Long getId() {
    return id;
  }

  Long getCustomerId() {
    return customerId;
  }

  CustomerSnapshot getCustomer() {
    return customer;
  }

  ShippingAddress getShippingAddress() {
    return shippingAddress;
  }

  OrderStatus getStatus() {
    return status;
  }

  DeliveryStatus getDeliveryStatus() {
    return deliveryStatus;
  }

  PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  BigDecimal getTotalAmount() {
    return totalAmount;
  }

  void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  List<OrderItem> getItems() {
    return List.copyOf(items);
  }
}
