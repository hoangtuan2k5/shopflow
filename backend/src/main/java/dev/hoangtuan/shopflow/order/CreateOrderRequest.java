package dev.hoangtuan.shopflow.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
    @NotNull @Valid Customer customer,
    @NotNull @Valid ShippingAddress shippingAddress,
    @NotBlank String paymentMethod,
    @NotEmpty List<@NotNull @Valid Item> items) {

  public record Customer(
      @NotBlank @Size(max = 255) String fullName,
      @Email @Size(max = 255) String email,
      @Size(max = 20) String phone) {}

  public record ShippingAddress(
      @NotBlank @Size(max = 255) String receiverName,
      @NotBlank @Size(max = 20) String phone,
      @NotBlank @Size(max = 500) String addressLine,
      @Size(max = 100) String district,
      @NotBlank @Size(max = 100) String city) {}

  public record Item(@NotNull Long productId, @NotNull @Positive Integer quantity) {}
}
