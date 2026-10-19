package dev.hoangtuan.shopflow.exception.handler;

import dev.hoangtuan.shopflow.controller.CatalogIndexController;
import dev.hoangtuan.shopflow.exception.search.SearchUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CatalogIndexController.class)
public class CatalogIndexErrorHandler {
  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorResponse> invalidQuery() {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("q must be nonblank and limit must be between 1 and 20", 400));
  }

  @ExceptionHandler(SearchUnavailableException.class)
  ResponseEntity<ErrorResponse> unavailable(SearchUnavailableException exception) {
    return ResponseEntity.status(503).body(new ErrorResponse(exception.getMessage(), 503));
  }

  private record ErrorResponse(String message, int status) {}
}
