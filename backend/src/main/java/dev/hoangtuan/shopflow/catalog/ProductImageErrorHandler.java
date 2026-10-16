package dev.hoangtuan.shopflow.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = ProductImageController.class)
class ProductImageErrorHandler {
  @ExceptionHandler(ProductImageException.class)
  ResponseEntity<ImageError> imageError(ProductImageException e) {
    return ResponseEntity.status(e.status).body(new ImageError(e.getMessage(), e.status));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ImageError> missingProduct() {
    return ResponseEntity.status(404).body(new ImageError("Product not found", 404));
  }

  record ImageError(String message, int status) {}
}

@RestControllerAdvice
class MultipartSizeErrorHandler {
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProductImageErrorHandler.ImageError> tooLarge() {
    return ResponseEntity.status(413)
        .body(new ProductImageErrorHandler.ImageError("Image exceeds 2 MiB limit", 413));
  }
}
