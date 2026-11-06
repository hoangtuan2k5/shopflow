package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.payment.CreatePaymentRequest;
import dev.hoangtuan.shopflow.dto.payment.PaymentResponse;
import dev.hoangtuan.shopflow.service.AuthService;
import dev.hoangtuan.shopflow.service.PaymentService;
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
@RequestMapping("/orders/{orderRef}/payments")
public class PaymentController {

  private final PaymentService paymentService;
  private final AuthService auth;

  PaymentController(PaymentService paymentService, AuthService auth) {
    this.paymentService = paymentService;
    this.auth = auth;
  }

  @PostMapping
  ResponseEntity<PaymentResponse> createPayment(
      @PathVariable String orderRef, @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPayment(orderRef, request, auth.currentUserId()));
  }
}
