package dev.hoangtuan.shopflow.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
    @NotNull Integer delta, @NotBlank @Size(max = 500) String reason) {}
