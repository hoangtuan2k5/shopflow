package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.ProductImageController;
import dev.hoangtuan.shopflow.exception.catalog.ProductImageException;
import dev.hoangtuan.shopflow.exception.catalog.ProductNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductImageController.class)
public class ProductImageErrorHandler {
  @ExceptionHandler(ProductImageException.class)
  ResponseEntity<ImageError> imageError(ProductImageException e) {
    return ResponseEntity.status(e.status()).body(new ImageError(e.getMessage(), e.status()));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ImageError> missingProduct() {
    return ResponseEntity.status(404).body(new ImageError("Product not found", 404));
  }

  record ImageError(String message, int status) {}
}
