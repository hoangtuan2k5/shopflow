package dev.hoangtuan.shopflow.dto.receiving;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReceivingRequest(
    @NotNull @Positive Long productId,
    @NotNull @Positive Integer quantity,
    String supplierName,
    String note) {}
