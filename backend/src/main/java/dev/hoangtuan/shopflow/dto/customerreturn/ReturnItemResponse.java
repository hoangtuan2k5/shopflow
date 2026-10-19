package dev.hoangtuan.shopflow.dto.customerreturn;

public record ReturnItemResponse(
    Long orderItemId, Long productId, String productName, int quantity) {}
