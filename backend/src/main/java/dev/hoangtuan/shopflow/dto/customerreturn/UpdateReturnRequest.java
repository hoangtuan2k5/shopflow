package dev.hoangtuan.shopflow.dto.customerreturn;

import dev.hoangtuan.shopflow.model.customerreturn.ReturnStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReturnRequest(@NotNull ReturnStatus toStatus, Boolean restockable) {}
