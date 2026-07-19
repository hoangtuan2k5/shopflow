package dev.hoangtuan.shopflow.order;

record InsufficientStock(Long productId, int requestedQuantity, int availableQuantity) {}
