package dev.hoangtuan.shopflow.customerreturn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReturnItemRequest(@NotNull Long orderItemId, @NotNull @Positive Integer quantity) {}
