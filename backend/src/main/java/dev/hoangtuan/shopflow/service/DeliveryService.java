package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.delivery.DeliveryItemResponse;
import dev.hoangtuan.shopflow.dto.delivery.DeliveryResponse;
import dev.hoangtuan.shopflow.dto.delivery.UpdateDeliveryRequest;
import dev.hoangtuan.shopflow.exception.delivery.DeliveryConflictException;
import dev.hoangtuan.shopflow.exception.delivery.DeliveryNotFoundException;
import dev.hoangtuan.shopflow.model.order.DeliveryStatus;
import dev.hoangtuan.shopflow.repository.jdbc.DeliveryRepository;
import dev.hoangtuan.shopflow.repository.jdbc.DeliveryRepository.OrderRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

  private final DeliveryRepository repository;

  public DeliveryService(DeliveryRepository repository) {
    this.repository = repository;
  }

  public List<DeliveryResponse> getDeliveries() {
    return repository.findAll().stream()
        // ponytail: MVP-sized queue; batch child reads when delivery volume needs pagination.
        .map(this::response)
        .toList();
  }

  @Transactional
  public DeliveryResponse update(Long orderId, UpdateDeliveryRequest request) {
    OrderRow order = repository.findAndLock(orderId);
    if (order == null) {
      throw new DeliveryNotFoundException();
    }
    if (!"PAID".equals(order.orderStatus())) {
      throw new DeliveryConflictException("Order must be PAID before delivery can start");
    }

    DeliveryStatus expected = next(order.deliveryStatus());
    if (request.toStatus() != expected) {
      throw new DeliveryConflictException(
          "Invalid delivery transition: " + order.deliveryStatus() + " -> " + request.toStatus());
    }

    if (request.toStatus() == DeliveryStatus.DELIVERED) {
      completeInventory(orderId);
    }

    int updated = repository.updateDelivery(orderId, order.deliveryStatus(), request.toStatus());
    if (updated != 1) {
      throw new IllegalStateException("Delivery status changed while updating order");
    }

    repository.recordHistory(orderId, order.deliveryStatus(), request.toStatus());

    return response(
        new OrderRow(
            order.orderId(),
            order.orderStatus(),
            request.toStatus(),
            order.receiverName(),
            order.city(),
            order.totalAmount(),
            order.createdAt()));
  }

  private DeliveryStatus next(DeliveryStatus current) {
    return switch (current) {
      case NONE -> DeliveryStatus.PREPARING;
      case PREPARING -> DeliveryStatus.SHIPPED;
      case SHIPPED -> DeliveryStatus.DELIVERED;
      case DELIVERED -> null;
    };
  }

  private void completeInventory(Long orderId) {
    List<DeliveryItemResponse> items = repository.items(orderId);
    for (DeliveryItemResponse item : items) {
      int updated = repository.completeInventory(item);
      if (updated != 1) {
        throw new DeliveryConflictException("Inventory cannot complete delivery");
      }

      repository.recordDelivery(orderId, item);
    }
  }

  private DeliveryResponse response(OrderRow order) {
    return new DeliveryResponse(
        order.orderId(),
        order.orderStatus(),
        order.deliveryStatus(),
        order.receiverName(),
        order.city(),
        order.totalAmount(),
        order.createdAt(),
        repository.items(order.orderId()),
        repository.history(order.orderId()));
  }
}
