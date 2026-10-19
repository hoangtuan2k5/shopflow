package dev.hoangtuan.shopflow.integration.ai;

import dev.hoangtuan.shopflow.exception.search.SearchUnavailableException;
import dev.hoangtuan.shopflow.model.search.ProductText;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiEmbeddingClient {
  private static final String MODEL = "models/gemini-embedding-2";
  private final RestClient client;
  private final String apiKey;
  private final int dimensions;

  public GeminiEmbeddingClient(
      RestClient.Builder builder,
      @Value("${shopflow.gemini.base-url}") String baseUrl,
      @Value("${shopflow.gemini.api-key}") String apiKey,
      @Value("${shopflow.qdrant.dimensions}") int dimensions) {
    this.client = builder.clone().baseUrl(baseUrl).defaultHeader("x-goog-api-key", apiKey).build();
    this.apiKey = apiKey;
    this.dimensions = dimensions;
  }

  public void requireKey() {
    if (apiKey.isBlank()) throw new SearchUnavailableException("GEMINI_API_KEY is not configured");
  }

  public List<List<Double>> products(List<ProductText> products) {
    requireKey();
    var requests =
        products.stream()
            .map(
                p ->
                    request(
                        "title: "
                            + p.name()
                            + " | text: "
                            + (p.description() == null ? "" : p.description())))
            .toList();
    try {
      var response =
          client
              .post()
              .uri("/" + MODEL + ":batchEmbedContents")
              .body(Map.of("requests", requests))
              .retrieve()
              .body(BatchResponse.class);
      if (response == null
          || response.embeddings() == null
          || response.embeddings().size() != products.size()) {
        throw new SearchUnavailableException("Gemini returned an invalid embedding batch");
      }
      return response.embeddings().stream().map(this::vector).toList();
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  public List<Double> query(String query) {
    requireKey();
    try {
      var response =
          client
              .post()
              .uri("/" + MODEL + ":embedContent")
              .body(request("task: search result | query: " + query))
              .retrieve()
              .body(QueryResponse.class);
      return vector(response == null ? null : response.embedding());
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  private Map<String, Object> request(String text) {
    return Map.of(
        "model",
        MODEL,
        "content",
        Map.of("parts", List.of(Map.of("text", text))),
        "embedContentConfig",
        Map.of("outputDimensionality", dimensions));
  }

  private List<Double> vector(Embedding embedding) {
    if (embedding == null
        || embedding.values() == null
        || embedding.values().size() != dimensions
        || embedding.values().stream().anyMatch(v -> v == null || !Double.isFinite(v))
        || embedding.values().stream().allMatch(v -> v == 0)) {
      throw new SearchUnavailableException("Gemini returned an invalid embedding vector");
    }
    return embedding.values();
  }

  private SearchUnavailableException failure(RestClientException exception) {
    return new SearchUnavailableException(
        "Gemini embedding unavailable"
            + (exception instanceof RestClientResponseException e
                ? " (HTTP " + e.getStatusCode().value() + ")"
                : ""));
  }

  private record Embedding(List<Double> values) {}

  private record BatchResponse(List<Embedding> embeddings) {}

  private record QueryResponse(Embedding embedding) {}
}
