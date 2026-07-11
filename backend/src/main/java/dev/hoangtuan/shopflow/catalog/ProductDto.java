package dev.hoangtuan.shopflow.catalog;

import java.math.BigDecimal;

public final class ProductDto {

  private ProductDto() {}

  public record ListItem(Long id, String name, BigDecimal price, StockStatus stockStatus) {}

  public record Detail(
      Long id, String name, String description, BigDecimal price, StockStatus stockStatus) {}

  public enum StockStatus {
    IN_STOCK,
    OUT_OF_STOCK
  }
}
