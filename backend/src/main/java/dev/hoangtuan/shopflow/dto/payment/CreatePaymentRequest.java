package dev.hoangtuan.shopflow.dto.payment;

import dev.hoangtuan.shopflow.model.payment.SimulatedPaymentResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
    @NotNull SimulatedPaymentResult result, @Size(max = 500) String failureReason) {}
