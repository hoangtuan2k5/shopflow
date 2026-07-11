package dev.hoangtuan.shopflow.catalog;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
  List<ProductDto.ListItem> getProducts() {
    return productService.getProducts();
  }

  @GetMapping("/{id}")
  ProductDto.Detail getProduct(@PathVariable Long id) {
    return productService.getProduct(id);
  }
}
