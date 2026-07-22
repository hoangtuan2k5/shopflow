package dev.hoangtuan.shopflow.receiving;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record ReceivingRequest(
    @NotNull @Positive Long productId,
    @NotNull @Positive Integer quantity,
    String supplierName,
    String note) {}
