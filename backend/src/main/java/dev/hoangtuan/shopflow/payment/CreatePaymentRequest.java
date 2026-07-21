package dev.hoangtuan.shopflow.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
    @NotNull SimulatedPaymentResult result, @Size(max = 500) String failureReason) {}
