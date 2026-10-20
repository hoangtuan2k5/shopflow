package dev.hoangtuan.shopflow.model.inventory;

import java.util.List;

public record StockValidationResult(
    List<Long> unavailableProductIds,
    List<InsufficientStock> insufficientItems,
    List<ValidatedStockItem> items) {

  public StockValidationResult {
    unavailableProductIds = List.copyOf(unavailableProductIds);
    insufficientItems = List.copyOf(insufficientItems);
    items = List.copyOf(items);
  }

  public boolean isValid() {
    return unavailableProductIds.isEmpty() && insufficientItems.isEmpty();
  }
}
