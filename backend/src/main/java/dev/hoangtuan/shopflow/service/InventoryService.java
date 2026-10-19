package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.inventory.InventoryResponse;
import dev.hoangtuan.shopflow.dto.inventory.LowStockThresholdRequest;
import dev.hoangtuan.shopflow.dto.inventory.StockAdjustmentRequest;
import dev.hoangtuan.shopflow.exception.inventory.InventoryConflictException;
import dev.hoangtuan.shopflow.exception.inventory.InventoryNotFoundException;
import dev.hoangtuan.shopflow.exception.inventory.InventoryValidationException;
import dev.hoangtuan.shopflow.repository.jdbc.InventoryRepository;
import dev.hoangtuan.shopflow.repository.jdbc.InventoryRepository.InventoryRow;
import dev.hoangtuan.shopflow.repository.jdbc.InventoryRepository.ProductRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private final InventoryRepository repository;

  public InventoryService(InventoryRepository repository) {
    this.repository = repository;
  }

  public List<InventoryResponse> getInventory() {
    return repository.findAll().stream()
        .map(
            row ->
                response(
                    row.productId(),
                    row.productName(),
                    row.onHandStock(),
                    row.reservedStock(),
                    row.lowStockThreshold()))
        .toList();
  }

  @Transactional
  public InventoryResponse adjust(Long productId, StockAdjustmentRequest request) {
    if (request.delta() == 0) {
      throw new InventoryValidationException("Delta must not be zero");
    }

    ProductRow product = repository.findProductForUpdate(productId);
    if (product == null) {
      throw new InventoryNotFoundException();
    }

    InventoryRow inventory = repository.findInventoryForUpdate(productId);
    if (inventory == null) {
      if (request.delta() < 0) {
        throw conflict();
      }
      repository.createInventory(productId);
      inventory = new InventoryRow(0, 0);
    }

    long newOnHand = (long) inventory.onHandStock() + request.delta();
    if (newOnHand < inventory.reservedStock() || newOnHand < 0 || newOnHand > Integer.MAX_VALUE) {
      throw conflict();
    }

    repository.updateStock(product.id(), (int) newOnHand);
    repository.recordAdjustment(productId, request.delta(), request.reason().strip());

    return response(
        product.id(),
        product.name(),
        (int) newOnHand,
        inventory.reservedStock(),
        product.lowStockThreshold());
  }

  @Transactional
  public InventoryResponse updateThreshold(Long productId, LowStockThresholdRequest request) {
    ProductRow product = repository.findProductForUpdate(productId);
    if (product == null) {
      throw new InventoryNotFoundException();
    }

    repository.updateThreshold(productId, request.lowStockThreshold());
    InventoryRow inventory = repository.findInventoryForUpdate(productId);
    if (inventory == null) {
      inventory = new InventoryRow(0, 0);
    }

    return response(
        productId,
        product.name(),
        inventory.onHandStock(),
        inventory.reservedStock(),
        request.lowStockThreshold());
  }

  private InventoryResponse response(
      Long productId,
      String productName,
      int onHandStock,
      int reservedStock,
      Integer lowStockThreshold) {
    int availableStock = onHandStock - reservedStock;
    return new InventoryResponse(
        productId,
        productName,
        onHandStock,
        reservedStock,
        availableStock,
        lowStockThreshold,
        lowStockThreshold != null && availableStock <= lowStockThreshold);
  }

  private InventoryConflictException conflict() {
    return new InventoryConflictException("Adjustment violates inventory constraints");
  }
}
