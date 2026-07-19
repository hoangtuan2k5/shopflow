package dev.hoangtuan.shopflow.order;

import java.math.BigDecimal;

record ValidatedStockItem(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    int availableQuantity) {}
