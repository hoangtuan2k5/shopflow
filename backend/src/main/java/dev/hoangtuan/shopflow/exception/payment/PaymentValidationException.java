package dev.hoangtuan.shopflow.exception.payment;

public class PaymentValidationException extends RuntimeException {

  public PaymentValidationException(String message) {
    super(message);
  }
}
