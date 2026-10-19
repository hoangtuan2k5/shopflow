package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.inventory.InventoryResponse;
import dev.hoangtuan.shopflow.dto.inventory.LowStockThresholdRequest;
import dev.hoangtuan.shopflow.dto.inventory.StockAdjustmentRequest;
import dev.hoangtuan.shopflow.service.InventoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private final InventoryService inventoryService;

  InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @GetMapping
  List<InventoryResponse> getInventory() {
    return inventoryService.getInventory();
  }

  @PostMapping("/{productId}/adjustments")
  InventoryResponse adjust(
      @PathVariable Long productId, @Valid @RequestBody StockAdjustmentRequest request) {
    return inventoryService.adjust(productId, request);
  }

  @PutMapping("/{productId}/threshold")
  InventoryResponse updateThreshold(
      @PathVariable Long productId, @Valid @RequestBody LowStockThresholdRequest request) {
    return inventoryService.updateThreshold(productId, request);
  }
}
