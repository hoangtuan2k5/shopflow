package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.CatalogController;
import dev.hoangtuan.shopflow.exception.catalog.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrorHandler {

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> handleInvalidQuery() {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("Invalid catalog query parameters", 400));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ErrorResponse> handleProductNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("Product not found", HttpStatus.NOT_FOUND.value()));
  }

  private record ErrorResponse(String message, int status) {}
}
