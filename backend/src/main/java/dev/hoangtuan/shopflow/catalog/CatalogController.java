package dev.hoangtuan.shopflow.catalog;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catalog")
@RestController
@RequestMapping("/products")
class CatalogController {

  private final ProductService productService;

  CatalogController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  ProductDto.Page getProducts(
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @PositiveOrZero long after,
      @RequestParam(defaultValue = "") @Size(max = 255) String q) {
    return productService.getProducts(limit, after, q);
  }

  @GetMapping("/{id}")
  ProductDto.Detail getProduct(@PathVariable Long id) {
    return productService.getProduct(id);
  }
}
