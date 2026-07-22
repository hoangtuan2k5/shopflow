package dev.hoangtuan.shopflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    OrderStatus status,
    DeliveryStatus deliveryStatus,
    PaymentMethod paymentMethod,
    BigDecimal totalAmount,
    Customer customer,
    ShippingAddress shippingAddress,
    List<Item> items,
    Instant createdAt) {

  public record Customer(String fullName, String email, String phone) {}

  public record ShippingAddress(
      String receiverName, String phone, String addressLine, String district, String city) {}

  public record Item(
      Long productId,
      String productName,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal) {}
}
