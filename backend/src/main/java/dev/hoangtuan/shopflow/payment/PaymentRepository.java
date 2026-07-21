package dev.hoangtuan.shopflow.payment;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, Long> {

  boolean existsByOrderId(Long orderId);
}
