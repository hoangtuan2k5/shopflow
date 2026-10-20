package dev.hoangtuan.shopflow.dto.delivery;

import dev.hoangtuan.shopflow.model.order.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryRequest(@NotNull DeliveryStatus toStatus) {}
