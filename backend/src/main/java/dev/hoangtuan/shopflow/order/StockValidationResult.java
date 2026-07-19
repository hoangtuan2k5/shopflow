package dev.hoangtuan.shopflow.order;

import java.util.List;

record StockValidationResult(
    List<Long> unavailableProductIds,
    List<InsufficientStock> insufficientItems,
    List<ValidatedStockItem> items) {

  StockValidationResult {
    unavailableProductIds = List.copyOf(unavailableProductIds);
    insufficientItems = List.copyOf(insufficientItems);
    items = List.copyOf(items);
  }

  boolean isValid() {
    return unavailableProductIds.isEmpty() && insufficientItems.isEmpty();
  }
}
