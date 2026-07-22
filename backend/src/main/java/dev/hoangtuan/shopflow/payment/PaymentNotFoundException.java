package dev.hoangtuan.shopflow.payment;

class PaymentNotFoundException extends RuntimeException {

  PaymentNotFoundException(String message) {
    super(message);
  }
}
