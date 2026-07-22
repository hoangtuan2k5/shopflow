package dev.hoangtuan.shopflow.payment;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment")
@RestController
@RequestMapping("/orders/{orderId}/payments")
class PaymentController {

  private final PaymentService paymentService;

  PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  ResponseEntity<PaymentResponse> createPayment(
      @PathVariable Long orderId, @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPayment(orderId, request));
  }
}
