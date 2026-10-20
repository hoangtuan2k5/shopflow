package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.ReceivingController;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingConflictException;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingNotFoundException;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReceivingController.class)
public class ReceivingErrorHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST, "Invalid supplier receiving", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body", Map.of());
  }

  @ExceptionHandler(ReceivingValidationException.class)
  ResponseEntity<ErrorResponse> handleValidation(ReceivingValidationException exception) {
    return response(
        HttpStatus.BAD_REQUEST,
        "Invalid supplier receiving",
        Map.of(exception.field(), exception.getMessage()));
  }

  @ExceptionHandler(ReceivingNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(ReceivingNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler({ReceivingConflictException.class, CannotAcquireLockException.class})
  ResponseEntity<ErrorResponse> handleConflict(Exception exception) {
    String message =
        exception instanceof ReceivingConflictException
            ? exception.getMessage()
            : "Receiving conflicts with current inventory";
    return response(HttpStatus.CONFLICT, message, Map.of());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, status.value(), fieldErrors));
  }

  private record ErrorResponse(String message, int status, Map<String, String> fieldErrors) {}
}
