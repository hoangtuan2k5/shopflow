package dev.hoangtuan.shopflow.exception.delivery;

public class DeliveryNotFoundException extends RuntimeException {
  public DeliveryNotFoundException() {
    super("Order not found");
  }
}
