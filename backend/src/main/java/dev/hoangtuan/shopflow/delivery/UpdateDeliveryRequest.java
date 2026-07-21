package dev.hoangtuan.shopflow.delivery;

import dev.hoangtuan.shopflow.order.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

record UpdateDeliveryRequest(@NotNull DeliveryStatus toStatus) {}
