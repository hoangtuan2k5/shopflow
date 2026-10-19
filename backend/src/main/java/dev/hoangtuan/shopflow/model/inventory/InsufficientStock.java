package dev.hoangtuan.shopflow.model.inventory;

public record InsufficientStock(Long productId, int requestedQuantity, int availableQuantity) {}
