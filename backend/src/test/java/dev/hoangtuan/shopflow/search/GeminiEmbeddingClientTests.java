package dev.hoangtuan.shopflow.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiEmbeddingClientTests {
  MockRestServiceServer server;
  GeminiEmbeddingClient client;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new GeminiEmbeddingClient(builder, "https://gemini.invalid/v1beta", "test-secret", 3);
  }

  @Test
  void formatsDocumentsAndDimensionsInsideEachBatchRequest() {
    server
        .expect(
            requestTo("https://gemini.invalid/v1beta/models/gemini-embedding-2:batchEmbedContents"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", "test-secret"))
        .andExpect(
            content()
                .json(
                    """
          {"requests":[
            {"model":"models/gemini-embedding-2","content":{"parts":[{"text":"title: HEPA | text: Bụi mịn"}]},"embedContentConfig":{"outputDimensionality":3}},
            {"model":"models/gemini-embedding-2","content":{"parts":[{"text":"title: Empty | text: "}]},"embedContentConfig":{"outputDimensionality":3}}
          ]}
          """,
                    true))
        .andRespond(
            withSuccess(
                "{\"embeddings\":[{\"values\":[1,0,0]},{\"values\":[0,1,0]}]}",
                MediaType.APPLICATION_JSON));
    assertThat(
            client.products(
                List.of(
                    new CatalogIndexService.ProductText(1, "HEPA", "Bụi mịn"),
                    new CatalogIndexService.ProductText(2, "Empty", null))))
        .hasSize(2);
    server.verify();
  }

  @Test
  void formatsSearchQueryWithoutDocumentPrefixOrTaskType() {
    server
        .expect(requestTo("https://gemini.invalid/v1beta/models/gemini-embedding-2:embedContent"))
        .andExpect(
            content()
                .json(
                    """
          {"model":"models/gemini-embedding-2","content":{"parts":[{"text":"task: search result | query: lọc bụi"}]},"embedContentConfig":{"outputDimensionality":3}}
          """,
                    true))
        .andRespond(
            withSuccess("{\"embedding\":{\"values\":[1,0,0]}}", MediaType.APPLICATION_JSON));
    assertThat(client.query("lọc bụi")).containsExactly(1.0, 0.0, 0.0);
    server.verify();
  }

  @Test
  void rejectsMissingKeyBeforeAnyRequest() {
    client = new GeminiEmbeddingClient(RestClient.builder(), "http://localhost:9", "", 768);
    assertThatThrownBy(() -> client.query("filter"))
        .isInstanceOf(SearchUnavailableException.class)
        .hasMessageContaining("not configured");
    server.verify();
  }

  @Test
  void rejectsWrongDimensionsAndWrongBatchCount() {
    server
        .expect(anything())
        .andRespond(withSuccess("{\"embedding\":{\"values\":[1,0]}}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.query("filter"))
        .isInstanceOf(SearchUnavailableException.class)
        .hasMessageContaining("invalid embedding vector");
    server.verify();
    setup();
    server
        .expect(anything())
        .andRespond(withSuccess("{\"embeddings\":[]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(
            () -> client.products(List.of(new CatalogIndexService.ProductText(1, "x", "y"))))
        .isInstanceOf(SearchUnavailableException.class)
        .hasMessageContaining("invalid embedding batch");
    server.verify();
  }

  @Test
  void hidesUpstreamBodyAndKeyOnFailure() {
    server
        .expect(anything())
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret test-secret"));
    assertThatThrownBy(() -> client.query("filter"))
        .isInstanceOf(SearchUnavailableException.class)
        .hasMessage("Gemini embedding unavailable (HTTP 429)");
    server.verify();
  }
}
