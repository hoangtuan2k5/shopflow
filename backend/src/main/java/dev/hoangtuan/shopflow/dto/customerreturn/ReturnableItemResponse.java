package dev.hoangtuan.shopflow.dto.customerreturn;

public record ReturnableItemResponse(
    Long orderItemId,
    Long productId,
    String productName,
    int quantity,
    int returnedQuantity,
    int returnableQuantity) {}
