package dev.hoangtuan.shopflow.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InventoryController.class)
class InventoryErrorHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST, "Invalid inventory adjustment", fieldErrors);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, InventoryValidationException.class})
  ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
    String message =
        exception instanceof InventoryValidationException
            ? exception.getMessage()
            : "Malformed request body";
    return response(HttpStatus.BAD_REQUEST, message, Map.of());
  }

  @ExceptionHandler(InventoryNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(InventoryNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler(InventoryConflictException.class)
  ResponseEntity<ErrorResponse> handleConflict(InventoryConflictException exception) {
    return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, status.value(), fieldErrors));
  }

  private record ErrorResponse(String message, int status, Map<String, String> fieldErrors) {}
}
