package dev.hoangtuan.shopflow.exception.access;

public class EmailUnavailableException extends RuntimeException {
  public EmailUnavailableException() {
    super("Authentication service unavailable");
  }
}
