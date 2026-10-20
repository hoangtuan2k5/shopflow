package dev.hoangtuan.shopflow.exception.receiving;

public class ReceivingNotFoundException extends RuntimeException {
  public ReceivingNotFoundException() {
    super("Product not found");
  }
}
