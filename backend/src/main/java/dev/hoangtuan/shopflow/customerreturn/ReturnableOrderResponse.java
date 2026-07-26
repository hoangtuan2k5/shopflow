package dev.hoangtuan.shopflow.customerreturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnableOrderResponse(
    Long orderId,
    String receiverName,
    String city,
    BigDecimal totalAmount,
    Instant createdAt,
    List<ReturnableItemResponse> items) {}
