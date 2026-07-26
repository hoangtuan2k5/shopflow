package dev.hoangtuan.shopflow.customerreturn;

public record ReturnItemResponse(
    Long orderItemId, Long productId, String productName, int quantity) {}
