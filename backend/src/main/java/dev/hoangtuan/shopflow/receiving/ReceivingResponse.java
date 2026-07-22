package dev.hoangtuan.shopflow.receiving;

import java.time.Instant;

record ReceivingResponse(
    Long id,
    Long productId,
    String productName,
    int quantity,
    String supplierName,
    String note,
    Instant createdAt,
    String createdBy,
    int onHandStock,
    int reservedStock,
    int availableStock) {}
