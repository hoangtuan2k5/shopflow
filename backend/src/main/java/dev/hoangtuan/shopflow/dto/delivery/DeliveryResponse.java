package dev.hoangtuan.shopflow.dto.delivery;

import dev.hoangtuan.shopflow.model.order.DeliveryStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DeliveryResponse(
    Long orderId,
    String orderStatus,
    DeliveryStatus deliveryStatus,
    String receiverName,
    String city,
    BigDecimal totalAmount,
    Instant createdAt,
    List<DeliveryItemResponse> items,
    List<DeliveryHistoryResponse> history) {}
