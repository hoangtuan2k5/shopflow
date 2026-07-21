package dev.hoangtuan.shopflow.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PaymentController.class)
class PaymentErrorHandler {

  @ExceptionHandler({PaymentValidationException.class, MethodArgumentNotValidException.class})
  ResponseEntity<ErrorResponse> handleValidation(Exception exception) {
    String message =
        exception instanceof PaymentValidationException
            ? exception.getMessage()
            : "Invalid payment request";
    return response(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(PaymentConflictException.class)
  ResponseEntity<ErrorResponse> handleConflict(PaymentConflictException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(message, status.value()));
  }

  private record ErrorResponse(String message, int status) {}
}
