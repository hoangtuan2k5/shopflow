package dev.hoangtuan.shopflow.order;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

  private final OrderRepository orderRepository;
  private final StockReservationService stockReservationService;

  OrderService(OrderRepository orderRepository, StockReservationService stockReservationService) {
    this.orderRepository = orderRepository;
    this.stockReservationService = stockReservationService;
  }

  @Transactional
  OrderResponse createOrder(CreateOrderRequest request) {
    validateRequest(request);

    List<StockRequest> stockRequests =
        request.items().stream()
            .map(item -> new StockRequest(item.productId(), item.quantity()))
            .toList();
    StockValidationResult validation = stockReservationService.lockAndValidate(stockRequests);
    if (!validation.isValid()) {
      String message =
          validation.unavailableProductIds().isEmpty()
              ? "Insufficient stock"
              : "Product not available";
      throw new OrderValidationException(message, validation);
    }

    Map<Long, ValidatedStockItem> productsById = new HashMap<>();
    for (ValidatedStockItem item : validation.items()) {
      ensureWholeVndPrice(item);
      productsById.put(item.productId(), item);
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    Order order =
        new Order(
            new CustomerSnapshot(
                request.customer().fullName(),
                request.customer().email(),
                request.customer().phone()),
            new ShippingAddress(
                request.shippingAddress().receiverName(),
                request.shippingAddress().phone(),
                request.shippingAddress().addressLine(),
                request.shippingAddress().district(),
                request.shippingAddress().city()),
            PaymentMethod.CARD,
            BigDecimal.ZERO);

    for (CreateOrderRequest.Item requestItem : request.items()) {
      ValidatedStockItem product = productsById.get(requestItem.productId());
      BigDecimal lineTotal =
          product.unitPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
      totalAmount = totalAmount.add(lineTotal);
      order.addItem(
          new OrderItem(
              product.productId(),
              product.productName(),
              product.unitPrice(),
              requestItem.quantity()));
    }

    order.setTotalAmount(totalAmount);
    Order savedOrder = orderRepository.saveAndFlush(order);
    stockReservationService.reserve(savedOrder.getId(), validation.items());
    return toResponse(savedOrder);
  }

  private void validateRequest(CreateOrderRequest request) {
    if (request == null || request.items() == null || request.items().isEmpty()) {
      throw new OrderValidationException("Order must contain at least one item");
    }
    if (!"CARD".equals(request.paymentMethod())) {
      throw new OrderValidationException("Invalid payment method");
    }

    Map<Long, Boolean> productIds = new HashMap<>();
    for (CreateOrderRequest.Item item : request.items()) {
      if (item == null
          || item.productId() == null
          || item.quantity() == null
          || item.quantity() <= 0) {
        throw new OrderValidationException("Invalid item quantity");
      }
      if (productIds.put(item.productId(), Boolean.TRUE) != null) {
        throw new OrderValidationException("Duplicate productId in items");
      }
    }
  }

  private void ensureWholeVndPrice(ValidatedStockItem item) {
    if (item.unitPrice().stripTrailingZeros().scale() > 0) {
      throw new OrderValidationException("Product price must be a whole VND amount");
    }
  }

  private OrderResponse toResponse(Order order) {
    List<OrderResponse.Item> items =
        order.getItems().stream()
            .map(
                item ->
                    new OrderResponse.Item(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
            .toList();
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getDeliveryStatus(),
        order.getPaymentMethod(),
        order.getTotalAmount(),
        new OrderResponse.Customer(
            order.getCustomer().getFullName(),
            order.getCustomer().getEmail(),
            order.getCustomer().getPhone()),
        new OrderResponse.ShippingAddress(
            order.getShippingAddress().getReceiverName(),
            order.getShippingAddress().getPhone(),
            order.getShippingAddress().getAddressLine(),
            order.getShippingAddress().getDistrict(),
            order.getShippingAddress().getCity()),
        items,
        order.getCreatedAt());
  }
}
