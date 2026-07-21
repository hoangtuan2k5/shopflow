package dev.hoangtuan.shopflow.inventory;

class InventoryNotFoundException extends RuntimeException {

  InventoryNotFoundException() {
    super("Product not found");
  }
}
