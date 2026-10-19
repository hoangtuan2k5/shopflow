package dev.hoangtuan.shopflow.dto.customerreturn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateReturnRequest(
    @NotNull Long orderId,
    String reason,
    @NotEmpty List<@Valid @NotNull ReturnItemRequest> items) {}
