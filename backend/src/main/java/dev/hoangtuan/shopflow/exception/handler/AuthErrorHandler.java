package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.AccountController;
import dev.hoangtuan.shopflow.controller.AuthController;
import dev.hoangtuan.shopflow.controller.GoogleLinkController;
import dev.hoangtuan.shopflow.controller.PasswordRecoveryController;
import dev.hoangtuan.shopflow.controller.RegistrationController;
import dev.hoangtuan.shopflow.exception.access.AccountActionException;
import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import dev.hoangtuan.shopflow.exception.access.InvalidCodeException;
import dev.hoangtuan.shopflow.exception.access.OtpRateLimitException;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {
      AuthController.class,
      AccountController.class,
      GoogleLinkController.class,
      RegistrationController.class,
      PasswordRecoveryController.class
    })
public class AuthErrorHandler {

  /**
   * Một thông báo duy nhất cho mọi lý do từ chối. Phân biệt được tài khoản nào tồn tại là một lỗ
   * hổng dò tài khoản (NFR-10).
   */
  private static final String REJECTED = "Invalid username or password";

  @ExceptionHandler(AccountActionException.class)
  ResponseEntity<ErrorResponse> handleAccountAction(AccountActionException exception) {
    return response(exception.status(), exception.getMessage(), Map.of());
  }

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

  @ExceptionHandler(CredentialsValidationException.class)
  ResponseEntity<ErrorResponse> handleCredentials(CredentialsValidationException exception) {
    return response(HttpStatus.BAD_REQUEST, "Invalid credentials request", exception.fieldErrors());
  }

  @ExceptionHandler(InvalidCodeException.class)
  ResponseEntity<ErrorResponse> handleInvalidCode() {
    return response(HttpStatus.BAD_REQUEST, "Invalid or expired code", Map.of());
  }

  @ExceptionHandler(EmailUnavailableException.class)
  ResponseEntity<ErrorResponse> handleEmailUnavailable() {
    return response(HttpStatus.SERVICE_UNAVAILABLE, "Authentication service unavailable", Map.of());
  }

  @ExceptionHandler(OtpRateLimitException.class)
  ResponseEntity<ErrorResponse> handleOtpLimit(OtpRateLimitException exception) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
        .body(new ErrorResponse("Too many authentication attempts", 429, Map.of()));
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(message, status.value(), fieldErrors));
  }

  private record ErrorResponse(String message, int status, Map<String, String> fieldErrors) {}
}
