package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.customerreturn.CreateReturnRequest;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnItemRequest;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnItemResponse;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnResponse;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnableItemResponse;
import dev.hoangtuan.shopflow.dto.customerreturn.ReturnableOrderResponse;
import dev.hoangtuan.shopflow.dto.customerreturn.UpdateReturnRequest;
import dev.hoangtuan.shopflow.exception.customerreturn.ReturnConflictException;
import dev.hoangtuan.shopflow.exception.customerreturn.ReturnNotFoundException;
import dev.hoangtuan.shopflow.exception.customerreturn.ReturnValidationException;
import dev.hoangtuan.shopflow.model.customerreturn.ReturnStatus;
import dev.hoangtuan.shopflow.repository.jdbc.ReturnRepository;
import dev.hoangtuan.shopflow.repository.jdbc.ReturnRepository.OrderRow;
import dev.hoangtuan.shopflow.repository.jdbc.ReturnRepository.OrderStateRow;
import dev.hoangtuan.shopflow.repository.jdbc.ReturnRepository.ReturnRow;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnService {

  private final ReturnRepository repository;

  public ReturnService(ReturnRepository repository) {
    this.repository = repository;
  }

  public List<ReturnResponse> list() {
    List<ReturnRow> returns = repository.findAll();
    return returns.stream().map(row -> response(row, repository.items(row.id()))).toList();
  }

  public List<ReturnableOrderResponse> returnableOrders() {
    List<OrderRow> orders = repository.findDeliveredOrders();
    return orders.stream()
        .map(
            order ->
                new ReturnableOrderResponse(
                    order.id(),
                    order.receiverName(),
                    order.city(),
                    order.totalAmount(),
                    order.createdAt(),
                    repository.returnableItems(order.id())))
        .toList();
  }

  @Transactional
  public ReturnResponse create(CreateReturnRequest request) {
    OrderStateRow order = repository.findOrderForUpdate(request.orderId());
    if (order == null) {
      throw new ReturnNotFoundException("Order not found");
    }
    if (!"DELIVERED".equals(order.deliveryStatus())) {
      throw new ReturnConflictException("Order is not delivered");
    }

    String reason = optionalText(request.reason(), "reason", 500);
    Set<Long> seenOrderItemIds = new HashSet<>();
    for (ReturnItemRequest item : request.items()) {
      if (!seenOrderItemIds.add(item.orderItemId())) {
        throw new ReturnValidationException("items", "Must not repeat an order item");
      }
    }

    Map<Long, ReturnableItemResponse> returnable = new LinkedHashMap<>();
    for (ReturnableItemResponse item : repository.returnableItems(request.orderId())) {
      returnable.put(item.orderItemId(), item);
    }
    for (ReturnItemRequest item : request.items()) {
      ReturnableItemResponse target = returnable.get(item.orderItemId());
      if (target == null) {
        throw new ReturnValidationException("items", "Must reference items of the order");
      }
      if (item.quantity() > target.returnableQuantity()) {
        throw new ReturnConflictException("Return exceeds purchased quantity");
      }
    }

    Long returnId = repository.insertReturn(request.orderId(), reason);
    for (ReturnItemRequest item : request.items()) {
      repository.insertItem(returnId, item.orderItemId(), item.quantity());
    }

    return fetch(returnId);
  }

  @Transactional
  public ReturnResponse update(Long returnId, UpdateReturnRequest request) {
    ReturnRow current = repository.findReturnForUpdate(returnId);
    if (current == null) {
      throw new ReturnNotFoundException("Return not found");
    }

    return switch (request.toStatus()) {
      case APPROVED -> approve(current, request.restockable());
      case REJECTED -> reject(current, request.restockable());
      case RESTOCKED -> restock(current, request.restockable());
      case REQUESTED -> throw new ReturnConflictException("Unsupported return transition");
    };
  }

  private ReturnResponse approve(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.REQUESTED);
    if (restockable == null) {
      throw new ReturnValidationException("restockable", "Required when approving");
    }
    repository.updateStatus(current.id(), current.status(), ReturnStatus.APPROVED, restockable);
    return fetch(current.id());
  }

  private ReturnResponse reject(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.REQUESTED);
    requireNoRestockable(restockable);
    repository.updateStatus(current.id(), current.status(), ReturnStatus.REJECTED, false);
    return fetch(current.id());
  }

  private ReturnResponse restock(ReturnRow current, Boolean restockable) {
    requireStatus(current, ReturnStatus.APPROVED);
    requireNoRestockable(restockable);
    if (!current.restockable()) {
      throw new ReturnConflictException("Return is not restockable");
    }

    List<ReturnItemResponse> items =
        repository.items(current.id()).stream()
            .sorted(Comparator.comparing(ReturnItemResponse::productId))
            .toList();
    for (ReturnItemResponse item : items) {
      restockItem(item, current.id());
    }
    repository.updateStatus(current.id(), current.status(), ReturnStatus.RESTOCKED, true);
    return fetch(current.id());
  }

  private void restockItem(ReturnItemResponse item, Long returnId) {
    repository.lockProduct(item.productId());
    Integer onHandStock = repository.findOnHandStockForUpdate(item.productId());
    if (onHandStock == null) {
      repository.createInventory(item.productId());
      onHandStock = 0;
    }

    long newOnHand = (long) onHandStock + item.quantity();
    if (newOnHand > Integer.MAX_VALUE) {
      throw new ReturnConflictException("Restock exceeds inventory limit");
    }

    repository.updateStock(item.productId(), (int) newOnHand);
    repository.recordRestock(item.productId(), item.quantity(), returnId);
  }

  private void requireStatus(ReturnRow current, ReturnStatus expected) {
    if (current.status() != expected) {
      throw new ReturnConflictException("Unsupported return transition");
    }
  }

  private void requireNoRestockable(Boolean restockable) {
    if (restockable != null) {
      throw new ReturnValidationException("restockable", "Only applies to approval");
    }
  }

  private ReturnResponse fetch(Long returnId) {
    ReturnRow row = repository.findById(returnId);
    return response(row, repository.items(returnId));
  }

  private ReturnResponse response(ReturnRow row, List<ReturnItemResponse> items) {
    return new ReturnResponse(
        row.id(),
        row.orderId(),
        row.status(),
        row.reason(),
        row.restockable(),
        row.createdAt(),
        row.updatedAt(),
        items);
  }

  private String optionalText(String value, String field, int maximumLength) {
    if (value == null) {
      return null;
    }

    String text = value.strip();
    if (text.isEmpty()) {
      throw new ReturnValidationException(field, "Must not be blank");
    }
    if (text.length() > maximumLength) {
      throw new ReturnValidationException(
          field, "Must be %d characters or fewer".formatted(maximumLength));
    }
    return text;
  }
}
