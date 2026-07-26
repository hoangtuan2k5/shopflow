package dev.hoangtuan.shopflow.inventory;

import jakarta.validation.constraints.PositiveOrZero;

public record LowStockThresholdRequest(@PositiveOrZero Integer lowStockThreshold) {}
