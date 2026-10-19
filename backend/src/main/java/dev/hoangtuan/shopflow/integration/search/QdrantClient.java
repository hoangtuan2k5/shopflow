package dev.hoangtuan.shopflow.integration.search;

import dev.hoangtuan.shopflow.dto.search.SearchResult;
import dev.hoangtuan.shopflow.exception.search.SearchUnavailableException;
import dev.hoangtuan.shopflow.model.search.ProductText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class QdrantClient {
  private final RestClient client;
  private final String path;
  private final int dimensions;

  public QdrantClient(
      RestClient.Builder builder,
      @Value("${shopflow.qdrant.url}") String url,
      @Value("${shopflow.qdrant.api-key}") String apiKey,
      @Value("${shopflow.qdrant.collection}") String collection,
      @Value("${shopflow.qdrant.dimensions}") int dimensions) {
    // Six REST operations do not need an additional SDK or gRPC dependency.
    var configured = builder.clone().baseUrl(url);
    if (!apiKey.isBlank()) configured.defaultHeader("api-key", apiKey);
    client = configured.build();
    path = "/collections/" + collection;
    this.dimensions = dimensions;
  }

  public void ensureCollection() {
    try {
      var response = client.get().uri(path + "/exists").retrieve().body(ExistsResponse.class);
      if (response == null || response.result() == null)
        throw new SearchUnavailableException("Invalid Qdrant collection response");
      if (!response.result().exists())
        client
            .put()
            .uri(path)
            .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
            .retrieve()
            .toBodilessEntity();
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  public void upsert(List<ProductText> products, List<List<Double>> vectors) {
    var points = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < products.size(); i++) {
      var p = products.get(i);
      points.add(
          Map.of(
              "id",
              p.id(),
              "vector",
              vectors.get(i),
              "payload",
              Map.of("productId", p.id(), "name", p.name())));
    }
    try {
      client
          .put()
          .uri(path + "/points?wait=true")
          .body(Map.of("points", points))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  public List<Long> ids() {
    // shortcut: scroll all IDs for a small catalog; page reconciliation when catalog exceeds 1000
    // products.
    var ids = new ArrayList<Long>();
    Long offset = null;
    do {
      Map<String, Object> body =
          new LinkedHashMap<>(Map.of("limit", 100, "with_payload", false, "with_vector", false));
      if (offset != null) body.put("offset", offset);
      try {
        var response =
            client
                .post()
                .uri(path + "/points/scroll")
                .body(body)
                .retrieve()
                .body(ScrollResponse.class);
        if (response == null || response.result() == null || response.result().points() == null)
          throw new SearchUnavailableException("Invalid Qdrant scroll response");
        ids.addAll(response.result().points().stream().map(PointId::id).toList());
        offset = response.result().next_page_offset();
      } catch (RestClientException e) {
        throw failure(e);
      }
    } while (offset != null);
    return ids;
  }

  public void delete(List<Long> ids) {
    if (ids.isEmpty()) return;
    try {
      client
          .post()
          .uri(path + "/points/delete?wait=true")
          .body(Map.of("points", ids))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  public List<SearchResult> search(List<Double> vector, int limit) {
    try {
      var response =
          client
              .post()
              .uri(path + "/points/query")
              .body(Map.of("query", vector, "limit", limit, "with_payload", true))
              .retrieve()
              .body(QueryResponse.class);
      if (response == null || response.result() == null || response.result().points() == null)
        throw new SearchUnavailableException("Invalid Qdrant query response");
      return response.result().points().stream()
          .map(p -> new SearchResult(p.id(), p.payload().name(), p.score()))
          .toList();
    } catch (RestClientException e) {
      throw failure(e);
    }
  }

  private SearchUnavailableException failure(RestClientException exception) {
    if (exception instanceof RestClientResponseException e) {
      String body = e.getResponseBodyAsString();
      if (e.getStatusCode().value() == 400 && body.contains("dim")) {
        return new SearchUnavailableException(
            "Qdrant dimension mismatch: "
                + body
                + ". Delete the derived collection and reindex with the configured model/dimensions.");
      }
      return new SearchUnavailableException(
          "Qdrant unavailable (HTTP "
              + e.getStatusCode().value()
              + "). Run POST /catalog-index if the collection is missing.");
    }
    return new SearchUnavailableException("Qdrant unavailable");
  }

  private record Exists(boolean exists) {}

  private record ExistsResponse(Exists result) {}

  private record PointId(long id) {}

  private record ScrollResult(List<PointId> points, Long next_page_offset) {}

  private record ScrollResponse(ScrollResult result) {}

  private record Payload(String name) {}

  private record ScoredPoint(long id, double score, Payload payload) {}

  private record QueryResult(List<ScoredPoint> points) {}

  private record QueryResponse(QueryResult result) {}
}
