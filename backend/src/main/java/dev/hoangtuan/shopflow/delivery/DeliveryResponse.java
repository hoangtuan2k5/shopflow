package dev.hoangtuan.shopflow.delivery;

import dev.hoangtuan.shopflow.order.DeliveryStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record DeliveryResponse(
    Long orderId,
    String orderStatus,
    DeliveryStatus deliveryStatus,
    String receiverName,
    String city,
    BigDecimal totalAmount,
    Instant createdAt,
    List<DeliveryItemResponse> items,
    List<DeliveryHistoryResponse> history) {}

record DeliveryItemResponse(Long productId, String productName, int quantity) {}

record DeliveryHistoryResponse(
    DeliveryStatus fromStatus, DeliveryStatus toStatus, Instant changedAt, String changedBy) {}
