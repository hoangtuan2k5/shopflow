package dev.hoangtuan.shopflow.dto.customerreturn;

import dev.hoangtuan.shopflow.model.customerreturn.ReturnStatus;
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
