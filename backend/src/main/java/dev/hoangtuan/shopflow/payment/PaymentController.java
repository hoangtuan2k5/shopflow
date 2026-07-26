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
@RequestMapping("/orders/{orderRef}/payments")
class PaymentController {

  private final PaymentService paymentService;

  PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  /**
   * Đơn hàng được định danh bằng tham chiếu không đoán được chứ không phải id tuần tự: thanh toán
   * mở cho khách vãng lai nên không thể chặn bằng vai trò, và id tuần tự sẽ cho phép người lạ dò ra
   * đơn của người khác.
   */
  @PostMapping
  ResponseEntity<PaymentResponse> createPayment(
      @PathVariable String orderRef, @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.ok(paymentService.createPayment(orderRef, request));
  }
}
