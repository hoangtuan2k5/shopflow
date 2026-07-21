package dev.hoangtuan.shopflow.payment;

import dev.hoangtuan.shopflow.order.PaymentMethod;
import dev.hoangtuan.shopflow.order.StockReservationService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentService {

  private final JdbcTemplate jdbcTemplate;
  private final PaymentRepository paymentRepository;
  private final StockReservationService stockReservationService;

  PaymentService(
      JdbcTemplate jdbcTemplate,
      PaymentRepository paymentRepository,
      StockReservationService stockReservationService) {
    this.jdbcTemplate = jdbcTemplate;
    this.paymentRepository = paymentRepository;
    this.stockReservationService = stockReservationService;
  }

  @Transactional
  PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) {
    validateRequest(request);
    OrderPaymentData order = findAndLockOrder(orderId);
    if (order == null) {
      throw new PaymentNotFoundException("Order not found");
    }
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
    updateOrderStatus(orderId, orderStatus);
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

  private OrderPaymentData findAndLockOrder(Long orderId) {
    List<OrderPaymentData> orders =
        jdbcTemplate.query(
            """
            SELECT status, payment_method, total_amount
            FROM shopflow.orders
            WHERE id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new OrderPaymentData(
                    resultSet.getString("status"),
                    PaymentMethod.valueOf(resultSet.getString("payment_method")),
                    resultSet.getBigDecimal("total_amount")),
            orderId);
    return orders.isEmpty() ? null : orders.getFirst();
  }

  private void updateOrderStatus(Long orderId, String status) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE shopflow.orders
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING_PAYMENT'
            """,
            status,
            orderId);
    if (updated != 1) {
      throw new IllegalStateException("Order status changed while processing payment");
    }
  }

  private record OrderPaymentData(String status, PaymentMethod method, BigDecimal amount) {}
}
