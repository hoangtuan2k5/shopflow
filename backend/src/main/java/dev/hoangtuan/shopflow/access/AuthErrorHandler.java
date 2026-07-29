package dev.hoangtuan.shopflow.access;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthErrorHandler {

  /**
   * Một thông báo duy nhất cho mọi lý do từ chối. Phân biệt được tài khoản nào tồn tại là một lỗ
   * hổng dò tài khoản (NFR-10).
   */
  private static final String REJECTED = "Invalid username or password";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST, "Invalid credentials request", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return response(HttpStatus.BAD_REQUEST, "Malformed request body", Map.of());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ErrorResponse> handleAuthenticationFailure() {
    return response(HttpStatus.UNAUTHORIZED, REJECTED, Map.of());
  }

  @ExceptionHandler(LoginRateLimiter.RateLimitExceededException.class)
  ResponseEntity<ErrorResponse> handleRateLimit(
      LoginRateLimiter.RateLimitExceededException exception) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
        .body(new ErrorResponse("Too many sign-in attempts", 429, Map.of()));
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, status.value(), fieldErrors));
  }

  private record ErrorResponse(String message, int status, Map<String, String> fieldErrors) {}
}
