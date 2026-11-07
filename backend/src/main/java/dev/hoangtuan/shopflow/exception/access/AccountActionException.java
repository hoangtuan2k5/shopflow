package dev.hoangtuan.shopflow.exception.access;

import org.springframework.http.HttpStatus;

public class AccountActionException extends RuntimeException {
  private final HttpStatus status;

  public AccountActionException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
