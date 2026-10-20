package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.DashboardController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardErrorHandler {
  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ErrorResponse> invalidQuery() {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("Invalid dashboard query parameters", 400));
  }

  private record ErrorResponse(String message, int status) {}
}
