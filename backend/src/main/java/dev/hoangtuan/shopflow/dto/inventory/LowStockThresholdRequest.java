package dev.hoangtuan.shopflow.dto.inventory;

import jakarta.validation.constraints.PositiveOrZero;

public record LowStockThresholdRequest(@PositiveOrZero Integer lowStockThreshold) {}
