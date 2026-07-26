package dev.hoangtuan.shopflow.customerreturn;

import jakarta.validation.constraints.NotNull;

public record UpdateReturnRequest(@NotNull ReturnStatus toStatus, Boolean restockable) {}
