package dev.hoangtuan.shopflow.dto.inventory;

public record InventoryResponse(
    Long productId,
    String productName,
    int onHandStock,
    int reservedStock,
    int availableStock,
    Integer lowStockThreshold,
    boolean lowStock) {}
