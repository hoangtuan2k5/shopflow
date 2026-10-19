package dev.hoangtuan.shopflow.exception.payment;

public class PaymentConflictException extends RuntimeException {

  public PaymentConflictException(String message) {
    super(message);
  }
}
