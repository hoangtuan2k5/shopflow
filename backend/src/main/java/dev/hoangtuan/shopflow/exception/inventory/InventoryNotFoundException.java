package dev.hoangtuan.shopflow.exception.inventory;

public class InventoryNotFoundException extends RuntimeException {

  public InventoryNotFoundException() {
    super("Product not found");
  }
}
