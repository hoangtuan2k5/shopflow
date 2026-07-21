package dev.hoangtuan.shopflow.payment;

import dev.hoangtuan.shopflow.order.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long id,
    Long orderId,
    PaymentMethod method,
    PaymentStatus status,
    BigDecimal amount,
    Instant paidAt,
    String failedReason,
    Instant createdAt,
    String orderStatus) {}
