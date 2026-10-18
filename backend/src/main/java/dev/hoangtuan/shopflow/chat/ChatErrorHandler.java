package dev.hoangtuan.shopflow.chat;

import dev.hoangtuan.shopflow.search.SearchUnavailableException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ChatController.class)
class ChatErrorHandler {
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Error> invalid() {
    return ResponseEntity.badRequest().body(new Error("Invalid chat request", 400));
  }

  @ExceptionHandler(ChatQuotaGuard.LimitReached.class)
  ResponseEntity<Error> limit(ChatQuotaGuard.LimitReached e) {
    return ResponseEntity.status(429)
        .header("Retry-After", Long.toString(e.retryAfter))
        .body(new Error("Chat daily limit reached", 429));
  }

  @ExceptionHandler({ChatUnavailableException.class, SearchUnavailableException.class})
  ResponseEntity<Error> unavailable() {
    return ResponseEntity.status(503).body(new Error("Product assistant unavailable", 503));
  }

  private record Error(String message, int status) {}
}
