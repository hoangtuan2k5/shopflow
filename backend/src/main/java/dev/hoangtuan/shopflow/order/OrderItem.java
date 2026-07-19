package dev.hoangtuan.shopflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items", schema = "shopflow")
class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "product_name", nullable = false, length = 255)
  private String productName;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  protected OrderItem() {}

  OrderItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
    this.productId = productId;
    this.productName = productName;
    this.unitPrice = unitPrice;
    this.quantity = quantity;
  }

  Long getId() {
    return id;
  }

  Order getOrder() {
    return order;
  }

  Long getProductId() {
    return productId;
  }

  String getProductName() {
    return productName;
  }

  BigDecimal getUnitPrice() {
    return unitPrice;
  }

  int getQuantity() {
    return quantity;
  }

  void attachTo(Order order) {
    this.order = order;
  }
}
