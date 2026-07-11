package dev.hoangtuan.shopflow.catalog;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class ProductService {

  private final ProductRepository productRepository;

  ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  List<ProductDto.ListItem> getProducts() {
    return productRepository.findAllActive().stream()
        .map(
            product ->
                new ProductDto.ListItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    ProductDto.StockStatus.valueOf(product.getStockStatus())))
        .toList();
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
                    ProductDto.StockStatus.valueOf(product.getStockStatus())))
        .orElseThrow(ProductNotFoundException::new);
  }
}
