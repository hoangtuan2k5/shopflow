package dev.hoangtuan.shopflow.dto.delivery;

import dev.hoangtuan.shopflow.model.order.DeliveryStatus;
import java.time.Instant;

public record DeliveryHistoryResponse(
    DeliveryStatus fromStatus, DeliveryStatus toStatus, Instant changedAt, String changedBy) {}
