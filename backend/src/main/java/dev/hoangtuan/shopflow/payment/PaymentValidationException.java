package dev.hoangtuan.shopflow.payment;

class PaymentValidationException extends RuntimeException {

  PaymentValidationException(String message) {
    super(message);
  }
}
