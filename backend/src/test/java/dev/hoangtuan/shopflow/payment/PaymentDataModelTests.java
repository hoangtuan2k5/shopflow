package dev.hoangtuan.shopflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.hoangtuan.shopflow.order.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class PaymentDataModelTests {

  @Autowired private PaymentRepository paymentRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.payments");
    jdbcTemplate.update("DELETE FROM shopflow.orders");
  }

  @Test
  void persistsPendingPaymentAgainstExistingSchema() {
    long orderId = insertOrder();

    Payment saved =
        paymentRepository.saveAndFlush(
            new Payment(orderId, PaymentMethod.CARD, new BigDecimal("2190000")));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getOrderId()).isEqualTo(orderId);
    assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CARD);
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(saved.getAmount()).isEqualByComparingTo("2190000");
    assertThat(saved.getPaidAt()).isNull();
    assertThat(saved.getFailedReason()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(paymentRepository.existsByOrderId(orderId)).isTrue();
  }

  @Test
  void resolvesPaymentOnlyOnce() {
    Payment payment =
        paymentRepository.saveAndFlush(
            new Payment(insertOrder(), PaymentMethod.CARD, new BigDecimal("2190000")));

    payment.resolve(SimulatedPaymentResult.SUCCESS, null);
    paymentRepository.flush();

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(payment.getPaidAt()).isNotNull();
    assertThat(payment.getFailedReason()).isNull();
    assertThatIllegalStateException()
        .isThrownBy(() -> payment.resolve(SimulatedPaymentResult.FAILED, "Declined"));
  }

  @Test
  void storesFailureDetailsForFailedAndExpiredResults() {
    Payment failed =
        paymentRepository.saveAndFlush(
            new Payment(insertOrder(), PaymentMethod.CARD, new BigDecimal("2190000")));
    failed.resolve(SimulatedPaymentResult.FAILED, "Declined by simulation");

    assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(failed.getPaidAt()).isNull();
    assertThat(failed.getFailedReason()).isEqualTo("Declined by simulation");

    paymentRepository.delete(failed);
    paymentRepository.flush();

    Payment expired =
        paymentRepository.saveAndFlush(
            new Payment(insertOrder(), PaymentMethod.CARD, new BigDecimal("2190000")));
    expired.resolve(SimulatedPaymentResult.EXPIRED, "Expired by simulation");

    assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    assertThat(expired.getPaidAt()).isNull();
    assertThat(expired.getFailedReason()).isEqualTo("Expired by simulation");
  }

  private long insertOrder() {
    jdbcTemplate.update(
        """
        INSERT INTO shopflow.orders (
          customer_name, receiver_name, receiver_phone, address_line, city,
          payment_method, total_amount
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        "Nguyen Van A",
        "Nguyen Van A",
        "0900000000",
        "1 Main Street",
        "HCMC",
        "CARD",
        new BigDecimal("2190000"));
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.orders", Long.class);
  }
}
