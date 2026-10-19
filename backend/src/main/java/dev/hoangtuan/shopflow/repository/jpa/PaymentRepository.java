package dev.hoangtuan.shopflow.repository.jpa;

import dev.hoangtuan.shopflow.model.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  boolean existsByOrderId(Long orderId);
}
