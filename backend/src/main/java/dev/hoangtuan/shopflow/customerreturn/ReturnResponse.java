package dev.hoangtuan.shopflow.customerreturn;

import java.time.Instant;
import java.util.List;

public record ReturnResponse(
    Long id,
    Long orderId,
    ReturnStatus status,
    String reason,
    boolean restockable,
    Instant createdAt,
    Instant updatedAt,
    List<ReturnItemResponse> items) {}
