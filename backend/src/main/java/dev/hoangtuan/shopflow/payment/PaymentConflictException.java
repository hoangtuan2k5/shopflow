package dev.hoangtuan.shopflow.payment;

class PaymentConflictException extends RuntimeException {

  PaymentConflictException(String message) {
    super(message);
  }
}
