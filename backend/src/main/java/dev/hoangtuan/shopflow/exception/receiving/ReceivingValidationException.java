package dev.hoangtuan.shopflow.exception.receiving;

public class ReceivingValidationException extends RuntimeException {
  private final String field;

  public ReceivingValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
