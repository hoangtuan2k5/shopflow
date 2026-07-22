package dev.hoangtuan.shopflow.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeliveryController.class)
class DeliveryErrorHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation() {
    return response(HttpStatus.BAD_REQUEST, "Invalid delivery request");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(DeliveryNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(DeliveryNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(DeliveryConflictException.class)
  ResponseEntity<ErrorResponse> handleConflict(DeliveryConflictException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(message, status.value()));
  }

  private record ErrorResponse(String message, int status) {}
}

class DeliveryNotFoundException extends RuntimeException {
  DeliveryNotFoundException() {
    super("Order not found");
  }
}

class DeliveryConflictException extends RuntimeException {
  DeliveryConflictException(String message) {
    super(message);
  }
}
