package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.order.CreateOrderRequest;
import dev.hoangtuan.shopflow.dto.order.OrderResponse;
import dev.hoangtuan.shopflow.service.AuthService;
import dev.hoangtuan.shopflow.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order")
@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final AuthService auth;

  OrderController(OrderService orderService, AuthService auth) {
    this.orderService = orderService;
    this.auth = auth;
  }

  @PostMapping
  ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orderService.createOrder(request, auth.currentUserId()));
  }
}
