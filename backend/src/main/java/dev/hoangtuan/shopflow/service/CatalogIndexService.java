package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.search.IndexResult;
import dev.hoangtuan.shopflow.dto.search.SearchResult;
import dev.hoangtuan.shopflow.integration.ai.GeminiEmbeddingClient;
import dev.hoangtuan.shopflow.integration.search.QdrantClient;
import dev.hoangtuan.shopflow.model.search.ProductText;
import dev.hoangtuan.shopflow.repository.jdbc.CatalogIndexRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogIndexService {
  private final CatalogIndexRepository repository;
  private final GeminiEmbeddingClient gemini;
  private final QdrantClient qdrant;
  private final int batchSize;
  private final int dimensions;

  public CatalogIndexService(
      CatalogIndexRepository repository,
      GeminiEmbeddingClient gemini,
      QdrantClient qdrant,
      @Value("${shopflow.gemini.batch-size}") int batchSize,
      @Value("${shopflow.qdrant.dimensions}") int dimensions) {
    if (batchSize < 1) throw new IllegalArgumentException("Embedding batch size must be positive");
    this.repository = repository;
    this.gemini = gemini;
    this.qdrant = qdrant;
    this.batchSize = batchSize;
    this.dimensions = dimensions;
  }

  public synchronized IndexResult index() {
    gemini.requireKey();
    var products = repository.findActiveProducts();
    qdrant.ensureCollection();
    // shortcut: synchronous full re-embedding without retry/cache; reconsider above 1000 products
    // or recurring 429s.
    for (int start = 0; start < products.size(); start += batchSize) {
      var batch = products.subList(start, Math.min(start + batchSize, products.size()));
      qdrant.upsert(batch, gemini.products(batch));
    }
    var active = new HashSet<>(products.stream().map(ProductText::id).toList());
    var removed = qdrant.ids().stream().filter(id -> !active.contains(id)).toList();
    qdrant.delete(removed);
    return new IndexResult(products.size(), removed.size(), dimensions);
  }

  public List<SearchResult> search(String query, int limit) {
    return qdrant.search(gemini.query(query), limit);
  }
}
