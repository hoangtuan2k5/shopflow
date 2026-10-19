package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.payment.CreatePaymentRequest;
import dev.hoangtuan.shopflow.dto.payment.PaymentResponse;
import dev.hoangtuan.shopflow.exception.payment.PaymentConflictException;
import dev.hoangtuan.shopflow.exception.payment.PaymentNotFoundException;
import dev.hoangtuan.shopflow.exception.payment.PaymentValidationException;
import dev.hoangtuan.shopflow.model.order.PaymentMethod;
import dev.hoangtuan.shopflow.model.payment.Payment;
import dev.hoangtuan.shopflow.model.payment.SimulatedPaymentResult;
import dev.hoangtuan.shopflow.repository.jdbc.PaymentOrderRepository;
import dev.hoangtuan.shopflow.repository.jdbc.PaymentOrderRepository.OrderPaymentData;
import dev.hoangtuan.shopflow.repository.jpa.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private final PaymentOrderRepository orders;
  private final PaymentRepository paymentRepository;
  private final StockReservationService stockReservationService;

  public PaymentService(
      PaymentOrderRepository orders,
      PaymentRepository paymentRepository,
      StockReservationService stockReservationService) {
    this.orders = orders;
    this.paymentRepository = paymentRepository;
    this.stockReservationService = stockReservationService;
  }

  @Transactional
  public PaymentResponse createPayment(String orderRef, CreatePaymentRequest request) {
    validateRequest(request);
    OrderPaymentData order = orders.findAndLockOrder(orderRef);
    if (order == null) {
      throw new PaymentNotFoundException("Order not found");
    }
    Long orderId = order.id();
    if (!"PENDING_PAYMENT".equals(order.status())
        || order.method() != PaymentMethod.CARD
        || paymentRepository.existsByOrderId(orderId)) {
      throw new PaymentConflictException("Order is not eligible for payment");
    }

    Payment payment =
        paymentRepository.saveAndFlush(new Payment(orderId, order.method(), order.amount()));
    payment.resolve(request.result(), request.failureReason());

    String orderStatus;
    if (request.result() == SimulatedPaymentResult.SUCCESS) {
      orderStatus = "PAID";
    } else {
      orderStatus = "PAYMENT_FAILED";
      stockReservationService.release(orderId);
    }
    orders.updateOrderStatus(orderId, orderStatus);
    paymentRepository.flush();

    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getMethod(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getPaidAt(),
        payment.getFailedReason(),
        payment.getCreatedAt(),
        orderStatus);
  }

  private void validateRequest(CreatePaymentRequest request) {
    if (request == null || request.result() == null) {
      throw new PaymentValidationException("Invalid payment request");
    }

    String failureReason = request.failureReason();
    if (request.result() == SimulatedPaymentResult.SUCCESS) {
      if (failureReason != null) {
        throw new PaymentValidationException("failureReason is not allowed for success");
      }
      return;
    }
    if (failureReason == null || failureReason.isBlank()) {
      throw new PaymentValidationException("failureReason is required");
    }
  }
}
