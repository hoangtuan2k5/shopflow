package dev.hoangtuan.shopflow.customerreturn;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReturnController.class)
class ReturnErrorHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST, "Invalid customer return", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body", Map.of());
  }

  @ExceptionHandler(ReturnValidationException.class)
  ResponseEntity<ErrorResponse> handleValidation(ReturnValidationException exception) {
    return response(
        HttpStatus.BAD_REQUEST,
        "Invalid customer return",
        Map.of(exception.field(), exception.getMessage()));
  }

  @ExceptionHandler(ReturnNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(ReturnNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
  }

  @ExceptionHandler({ReturnConflictException.class, CannotAcquireLockException.class})
  ResponseEntity<ErrorResponse> handleConflict(Exception exception) {
    String message =
        exception instanceof ReturnConflictException
            ? exception.getMessage()
            : "Return conflicts with current state";
    return response(HttpStatus.CONFLICT, message, Map.of());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, status.value(), fieldErrors));
  }

  private record ErrorResponse(String message, int status, Map<String, String> fieldErrors) {}
}

class ReturnValidationException extends RuntimeException {

  private final String field;

  ReturnValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  String field() {
    return field;
  }
}

class ReturnNotFoundException extends RuntimeException {

  ReturnNotFoundException(String message) {
    super(message);
  }
}

class ReturnConflictException extends RuntimeException {

  ReturnConflictException(String message) {
    super(message);
  }
}
