package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.catalog.ProductDto;
import dev.hoangtuan.shopflow.integration.storage.ProductImageStorage;
import dev.hoangtuan.shopflow.repository.jpa.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogReader {
  private final ProductRepository repository;
  private final ProductImageStorage images;

  public CatalogReader(ProductRepository repository, ProductImageStorage images) {
    this.repository = repository;
    this.images = images;
  }

  public List<ProductDto.Detail> findActiveByIds(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return repository.findActiveByIds(ids).stream()
        .map(
            p ->
                new ProductDto.Detail(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getPrice(),
                    ProductDto.StockStatus.valueOf(p.getStockStatus()),
                    images.url(p.getImageKey())))
        .toList();
  }
}
