package dev.hoangtuan.shopflow.catalog;

import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class ProductService {

  private final ProductRepository productRepository;

  private final ProductImageStorage imageStorage;

  ProductService(ProductRepository productRepository, ProductImageStorage imageStorage) {
    this.imageStorage = imageStorage;
    this.productRepository = productRepository;
  }

  ProductDto.Page getProducts(int limit, long after, String query) {
    String pattern =
        "%"
            + query
                .strip()
                .toLowerCase(Locale.forLanguageTag("vi"))
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    var rows = productRepository.findActivePage(after, pattern, PageRequest.of(0, limit + 1));
    var items =
        rows.stream()
            .limit(limit)
            .map(
                product ->
                    new ProductDto.ListItem(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        ProductDto.StockStatus.valueOf(product.getStockStatus()),
                        imageStorage.url(product.getImageKey())))
            .toList();
    return new ProductDto.Page(items, rows.size() > limit ? items.getLast().id().toString() : null);
  }

  ProductDto.Detail getProduct(Long id) {
    return productRepository
        .findActiveById(id)
        .map(
            product ->
                new ProductDto.Detail(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    ProductDto.StockStatus.valueOf(product.getStockStatus()),
                    imageStorage.url(product.getImageKey())))
        .orElseThrow(ProductNotFoundException::new);
  }
}
