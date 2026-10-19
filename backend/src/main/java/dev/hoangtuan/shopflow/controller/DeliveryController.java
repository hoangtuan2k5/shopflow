package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.delivery.DeliveryResponse;
import dev.hoangtuan.shopflow.dto.delivery.UpdateDeliveryRequest;
import dev.hoangtuan.shopflow.service.DeliveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Delivery")
@RestController
public class DeliveryController {

  private final DeliveryService deliveryService;

  DeliveryController(DeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @GetMapping("/deliveries")
  List<DeliveryResponse> getDeliveries() {
    return deliveryService.getDeliveries();
  }

  @PatchMapping("/orders/{orderId}/delivery")
  DeliveryResponse update(
      @PathVariable Long orderId, @Valid @RequestBody UpdateDeliveryRequest request) {
    return deliveryService.update(orderId, request);
  }
}
