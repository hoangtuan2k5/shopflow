package dev.hoangtuan.shopflow.exception.customerreturn;

public class ReturnValidationException extends RuntimeException {

  private final String field;

  public ReturnValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
