package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.model.inventory.InsufficientStock;
import dev.hoangtuan.shopflow.model.inventory.StockRequest;
import dev.hoangtuan.shopflow.model.inventory.StockValidationResult;
import dev.hoangtuan.shopflow.model.inventory.ValidatedStockItem;
import dev.hoangtuan.shopflow.repository.jdbc.StockReservationRepository;
import dev.hoangtuan.shopflow.repository.jdbc.StockReservationRepository.ReleaseItem;
import dev.hoangtuan.shopflow.repository.jdbc.StockReservationRepository.StockRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {

  private final StockReservationRepository repository;

  public StockReservationService(StockReservationRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public StockValidationResult lockAndValidate(List<StockRequest> requests) {
    validateRequests(requests);

    List<Long> unavailableProductIds = new ArrayList<>();
    List<InsufficientStock> insufficientItems = new ArrayList<>();
    List<ValidatedStockItem> validItems = new ArrayList<>();

    requests.stream()
        .sorted(Comparator.comparing(StockRequest::productId))
        .forEach(
            request -> {
              StockRow row = repository.findAndLock(request.productId());
              if (row == null || !row.active()) {
                unavailableProductIds.add(request.productId());
                return;
              }

              if (row.onHandStock() == null) {
                insufficientItems.add(
                    new InsufficientStock(request.productId(), request.quantity(), 0));
                return;
              }

              int available = row.onHandStock() - row.reservedStock();
              if (available < request.quantity()) {
                insufficientItems.add(
                    new InsufficientStock(request.productId(), request.quantity(), available));
                return;
              }

              validItems.add(
                  new ValidatedStockItem(
                      row.id(), row.name(), row.price(), request.quantity(), available));
            });

    return new StockValidationResult(unavailableProductIds, insufficientItems, validItems);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void reserve(Long orderId, List<ValidatedStockItem> items) {
    if (orderId == null) {
      throw new IllegalArgumentException("orderId is required");
    }

    for (ValidatedStockItem item : items) {
      int updated = repository.reserveProduct(item);
      if (updated != 1) {
        throw new IllegalStateException(
            "Stock changed while reserving product " + item.productId());
      }

      repository.recordReservation(orderId, item);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void release(Long orderId) {
    List<ReleaseItem> items = repository.findReleaseItems(orderId);

    for (ReleaseItem item : items) {
      int updated = repository.releaseProduct(item);
      if (updated != 1) {
        throw new IllegalStateException(
            "Reserved stock is inconsistent for product " + item.productId());
      }

      repository.recordRelease(orderId, item);
    }
  }

  private void validateRequests(List<StockRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new IllegalArgumentException("at least one stock request is required");
    }

    Set<Long> productIds = new HashSet<>();
    for (StockRequest request : requests) {
      if (request == null || request.productId() == null || request.quantity() <= 0) {
        throw new IllegalArgumentException(
            "stock request must have a product and positive quantity");
      }
      if (!productIds.add(request.productId())) {
        throw new IllegalArgumentException("duplicate product id: " + request.productId());
      }
    }
  }
}
