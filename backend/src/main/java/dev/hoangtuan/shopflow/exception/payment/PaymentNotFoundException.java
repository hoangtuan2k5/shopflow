package dev.hoangtuan.shopflow.exception.payment;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(String message) {
    super(message);
  }
}
