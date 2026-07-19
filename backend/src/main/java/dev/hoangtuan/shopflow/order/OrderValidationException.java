package dev.hoangtuan.shopflow.order;

import java.util.List;

class OrderValidationException extends RuntimeException {

  private final List<Long> unavailableProductIds;
  private final List<InsufficientStock> insufficientItems;

  OrderValidationException(String message) {
    this(message, List.of(), List.of());
  }

  OrderValidationException(String message, StockValidationResult result) {
    this(message, result.unavailableProductIds(), result.insufficientItems());
  }

  private OrderValidationException(
      String message, List<Long> unavailableProductIds, List<InsufficientStock> insufficientItems) {
    super(message);
    this.unavailableProductIds = List.copyOf(unavailableProductIds);
    this.insufficientItems = List.copyOf(insufficientItems);
  }

  List<Long> getUnavailableProductIds() {
    return unavailableProductIds;
  }

  List<InsufficientStock> getInsufficientItems() {
    return insufficientItems;
  }
}
