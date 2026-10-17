package dev.hoangtuan.shopflow.catalog;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDto {

  private ProductDto() {}

  public record Page(List<ListItem> items, String nextCursor) {}

  public record ListItem(
      Long id, String name, BigDecimal price, StockStatus stockStatus, String imageUrl) {}

  public record Detail(
      Long id,
      String name,
      String description,
      BigDecimal price,
      StockStatus stockStatus,
      String imageUrl) {}

  public enum StockStatus {
    IN_STOCK,
    OUT_OF_STOCK
  }
}
