package dev.hoangtuan.shopflow.exception.catalog;

public class ProductImageException extends RuntimeException {
  private final int status;

  public ProductImageException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
