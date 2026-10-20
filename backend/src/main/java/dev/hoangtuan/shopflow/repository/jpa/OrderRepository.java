package dev.hoangtuan.shopflow.repository.jpa;

import dev.hoangtuan.shopflow.model.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
