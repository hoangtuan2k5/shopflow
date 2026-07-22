package dev.hoangtuan.shopflow.inventory;

public record InventoryResponse(
    Long productId, String productName, int onHandStock, int reservedStock, int availableStock) {}
