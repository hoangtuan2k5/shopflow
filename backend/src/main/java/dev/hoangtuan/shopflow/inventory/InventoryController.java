package dev.hoangtuan.shopflow.inventory;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/inventory")
class InventoryController {

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
}
