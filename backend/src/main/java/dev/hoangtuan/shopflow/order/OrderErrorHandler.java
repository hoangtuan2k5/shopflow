package dev.hoangtuan.shopflow.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
class OrderErrorHandler {

  @ExceptionHandler(OrderValidationException.class)
  ResponseEntity<ErrorResponse> handleOrderValidation(OrderValidationException exception) {
    return badRequest(
        new ErrorResponse(
            exception.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            exception.getUnavailableProductIds(),
            toInsufficientItems(exception.getInsufficientItems()),
            Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return badRequest(
        new ErrorResponse(
            "Invalid order request",
            HttpStatus.BAD_REQUEST.value(),
            List.of(),
            List.of(),
            fieldErrors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleMalformedBody() {
    return badRequest(
        new ErrorResponse(
            "Malformed request body",
            HttpStatus.BAD_REQUEST.value(),
            List.of(),
            List.of(),
            Map.of()));
  }

  private ResponseEntity<ErrorResponse> badRequest(ErrorResponse response) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  private List<InsufficientItem> toInsufficientItems(List<InsufficientStock> items) {
    return items.stream()
        .map(
            item ->
                new InsufficientItem(
                    item.productId(), item.requestedQuantity(), item.availableQuantity()))
        .toList();
  }

  private record ErrorResponse(
      String message,
      int status,
      List<Long> unavailableProductIds,
      List<InsufficientItem> insufficientItems,
      Map<String, String> fieldErrors) {}

  private record InsufficientItem(Long productId, int requestedQuantity, int availableStock) {}
}
