package dev.hoangtuan.shopflow.exception.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class MultipartSizeErrorHandler {
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProductImageErrorHandler.ImageError> tooLarge() {
    return ResponseEntity.status(413)
        .body(new ProductImageErrorHandler.ImageError("Image exceeds 2 MiB limit", 413));
  }
}
