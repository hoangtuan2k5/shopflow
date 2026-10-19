package dev.hoangtuan.shopflow.integration.search;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import dev.hoangtuan.shopflow.exception.search.SearchUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QdrantClientTests {
  @Test
  void scrollsAllPagesAndDeletesExplicitStaleIdsWithAuthentication() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new QdrantClient(builder, "http://qdrant.invalid", "private-key", "products", 3);
    server
        .expect(requestTo("http://qdrant.invalid/collections/products/points/scroll"))
        .andExpect(header("api-key", "private-key"))
        .andExpect(
            content().json("{\"limit\":100,\"with_payload\":false,\"with_vector\":false}", true))
        .andRespond(
            withSuccess(
                "{\"result\":{\"points\":[{\"id\":1}],\"next_page_offset\":99}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(anything())
        .andExpect(
            content()
                .json(
                    "{\"limit\":100,\"with_payload\":false,\"with_vector\":false,\"offset\":99}",
                    true))
        .andRespond(
            withSuccess(
                "{\"result\":{\"points\":[{\"id\":99}],\"next_page_offset\":null}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://qdrant.invalid/collections/products/points/delete?wait=true"))
        .andExpect(content().json("{\"points\":[99]}", true))
        .andRespond(withSuccess());
    assertThat(client.ids()).containsExactly(1L, 99L);
    client.delete(List.of(99L));
    server.verify();
  }

  @Test
  void explainsDimensionMismatchWithoutPretendingTheIndexSucceeded() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new QdrantClient(builder, "http://qdrant.invalid", "", "products", 3);
    server
        .expect(anything())
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("expected dim: 768, got 3"));
    assertThatThrownBy(() -> client.search(List.of(1.0, 0.0, 0.0), 5))
        .isInstanceOf(SearchUnavailableException.class)
        .hasMessageContaining("expected dim: 768, got 3")
        .hasMessageContaining("reindex");
    server.verify();
  }
}
