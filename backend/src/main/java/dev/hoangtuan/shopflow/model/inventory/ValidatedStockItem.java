package dev.hoangtuan.shopflow.model.inventory;

import java.math.BigDecimal;

public record ValidatedStockItem(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    int availableQuantity) {}
