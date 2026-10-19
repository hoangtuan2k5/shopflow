package dev.hoangtuan.shopflow.model.payment;

import dev.hoangtuan.shopflow.model.order.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", schema = "shopflow")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "failed_reason", length = 500)
  private String failedReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Payment() {}

  public Payment(Long orderId, PaymentMethod method, BigDecimal amount) {
    this.orderId = orderId;
    this.method = method;
    this.amount = amount;
  }

  @jakarta.persistence.PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public void resolve(SimulatedPaymentResult result, String failureReason) {
    if (status != PaymentStatus.PENDING) {
      throw new IllegalStateException("Payment is already resolved");
    }

    status = PaymentStatus.valueOf(result.name());
    if (result == SimulatedPaymentResult.SUCCESS) {
      paidAt = Instant.now();
    } else {
      failedReason = failureReason;
    }
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public String getFailedReason() {
    return failedReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
