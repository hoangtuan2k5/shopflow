package dev.hoangtuan.shopflow.search;

import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class CatalogIndexService {
  private final JdbcTemplate jdbc;
  private final GeminiEmbeddingClient gemini;
  private final QdrantClient qdrant;
  private final int batchSize;
  private final int dimensions;

  CatalogIndexService(
      JdbcTemplate jdbc,
      GeminiEmbeddingClient gemini,
      QdrantClient qdrant,
      @Value("${shopflow.gemini.batch-size}") int batchSize,
      @Value("${shopflow.qdrant.dimensions}") int dimensions) {
    if (batchSize < 1) throw new IllegalArgumentException("Embedding batch size must be positive");
    this.jdbc = jdbc;
    this.gemini = gemini;
    this.qdrant = qdrant;
    this.batchSize = batchSize;
    this.dimensions = dimensions;
  }

  synchronized IndexResult index() {
    gemini.requireKey();
    var products =
        jdbc.query(
            "SELECT id, name, description FROM shopflow.products WHERE active = TRUE ORDER BY id",
            (rs, row) ->
                new ProductText(
                    rs.getLong("id"), rs.getString("name"), rs.getString("description")));
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

  List<SearchResult> search(String query, int limit) {
    return qdrant.search(gemini.query(query), limit);
  }

  record ProductText(long id, String name, String description) {}

  record IndexResult(int indexed, int removed, int dimensions) {}

  record SearchResult(long productId, String name, double score) {}
}
