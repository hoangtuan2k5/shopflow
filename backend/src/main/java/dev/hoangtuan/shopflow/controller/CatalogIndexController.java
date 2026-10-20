package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.search.IndexResult;
import dev.hoangtuan.shopflow.dto.search.SearchResult;
import dev.hoangtuan.shopflow.service.CatalogIndexService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogIndexController {
  private final CatalogIndexService service;

  CatalogIndexController(CatalogIndexService service) {
    this.service = service;
  }

  @PostMapping("/catalog-index")
  IndexResult index() {
    return service.index();
  }

  @GetMapping("/catalog-index/search")
  List<SearchResult> search(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
    if (q.isBlank() || limit < 1 || limit > 20) {
      throw new IllegalArgumentException("q must be nonblank and limit must be between 1 and 20");
    }
    return service.search(q.trim(), limit);
  }
}
