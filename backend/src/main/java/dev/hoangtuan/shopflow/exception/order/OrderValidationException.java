package dev.hoangtuan.shopflow.exception.order;

import dev.hoangtuan.shopflow.model.inventory.InsufficientStock;
import dev.hoangtuan.shopflow.model.inventory.StockValidationResult;
import java.util.List;

public class OrderValidationException extends RuntimeException {

  private final List<Long> unavailableProductIds;
  private final List<InsufficientStock> insufficientItems;

  public OrderValidationException(String message) {
    this(message, List.of(), List.of());
  }

  public OrderValidationException(String message, StockValidationResult result) {
    this(message, result.unavailableProductIds(), result.insufficientItems());
  }

  private OrderValidationException(
      String message, List<Long> unavailableProductIds, List<InsufficientStock> insufficientItems) {
    super(message);
    this.unavailableProductIds = List.copyOf(unavailableProductIds);
    this.insufficientItems = List.copyOf(insufficientItems);
  }

  public List<Long> getUnavailableProductIds() {
    return unavailableProductIds;
  }

  public List<InsufficientStock> getInsufficientItems() {
    return insufficientItems;
  }
}
