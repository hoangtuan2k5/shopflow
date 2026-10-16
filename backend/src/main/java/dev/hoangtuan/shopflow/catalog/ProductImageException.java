package dev.hoangtuan.shopflow.catalog;

class ProductImageException extends RuntimeException {
  final int status;

  ProductImageException(int status, String message) {
    super(message);
    this.status = status;
  }
}
