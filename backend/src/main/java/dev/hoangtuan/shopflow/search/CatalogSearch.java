package dev.hoangtuan.shopflow.search;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogSearch {
  private final GeminiEmbeddingClient gemini;
  private final QdrantClient qdrant;

  CatalogSearch(
      @Value("${shopflow.gemini.base-url}") String baseUrl,
      @Value("${shopflow.gemini.api-key}") String key,
      @Value("${shopflow.qdrant.url}") String url,
      @Value("${shopflow.qdrant.api-key}") String qdrantKey,
      @Value("${shopflow.qdrant.collection}") String collection,
      @Value("${shopflow.qdrant.dimensions}") int dimensions) {
    // Chat uses shorter network limits; owner reindex retains its existing timeout.
    gemini =
        new GeminiEmbeddingClient(
            SearchConfiguration.builder(Duration.ofSeconds(3)), baseUrl, key, dimensions);
    qdrant =
        new QdrantClient(
            SearchConfiguration.builder(Duration.ofSeconds(1)),
            url,
            qdrantKey,
            collection,
            dimensions);
  }

  public List<Match> search(String query) {
    return qdrant.search(gemini.query(query), 5).stream()
        .map(p -> new Match(p.productId(), p.score()))
        .toList();
  }

  public record Match(long productId, double score) {}
}
