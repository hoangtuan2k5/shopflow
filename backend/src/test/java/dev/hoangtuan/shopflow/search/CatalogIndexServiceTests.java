package dev.hoangtuan.shopflow.search;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogIndexServiceTests {
  JdbcTemplate jdbc = mock(JdbcTemplate.class);
  GeminiEmbeddingClient gemini = mock(GeminiEmbeddingClient.class);
  QdrantClient qdrant = mock(QdrantClient.class);
  CatalogIndexService service = new CatalogIndexService(jdbc, gemini, qdrant, 2, 3);
  List<CatalogIndexService.ProductText> products =
      List.of(
          new CatalogIndexService.ProductText(1, "a", "a"),
          new CatalogIndexService.ProductText(2, "b", "b"),
          new CatalogIndexService.ProductText(3, "c", "c"));

  @BeforeEach
  void setup() {
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<CatalogIndexService.ProductText>>any()))
        .thenReturn(products);
    when(gemini.products(anyList()))
        .thenAnswer(
            inv ->
                ((List<?>) inv.getArgument(0)).stream().map(p -> List.of(1.0, 0.0, 0.0)).toList());
  }

  @Test
  void batchesUpsertsBeforeRemovingOnlyStaleIds() {
    when(qdrant.ids()).thenReturn(List.of(1L, 2L, 3L, 99L));
    assertThat(service.index()).isEqualTo(new CatalogIndexService.IndexResult(3, 1, 3));
    var order = inOrder(gemini, qdrant);
    order.verify(gemini).requireKey();
    order.verify(qdrant).ensureCollection();
    order.verify(gemini).products(products.subList(0, 2));
    order.verify(qdrant).upsert(eq(products.subList(0, 2)), anyList());
    order.verify(gemini).products(products.subList(2, 3));
    order.verify(qdrant).upsert(eq(products.subList(2, 3)), anyList());
    order.verify(qdrant).ids();
    order.verify(qdrant).delete(List.of(99L));
  }

  @Test
  void neverDeletesAfterSecondBatchFails() {
    when(gemini.products(products.subList(2, 3)))
        .thenThrow(new SearchUnavailableException("Gemini unavailable"));
    assertThatThrownBy(service::index).isInstanceOf(SearchUnavailableException.class);
    verify(qdrant, times(1)).upsert(anyList(), anyList());
    verify(qdrant, never()).ids();
    verify(qdrant, never()).delete(anyList());
  }

  @Test
  void neverDeletesAfterUpsertFails() {
    doThrow(new SearchUnavailableException("Qdrant unavailable"))
        .when(qdrant)
        .upsert(anyList(), anyList());
    assertThatThrownBy(service::index).isInstanceOf(SearchUnavailableException.class);
    verify(qdrant, never()).ids();
    verify(qdrant, never()).delete(anyList());
  }

  @Test
  void emptyCatalogRemovesAllOldPointsWithoutEmbedding() {
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<CatalogIndexService.ProductText>>any()))
        .thenReturn(List.of());
    when(qdrant.ids()).thenReturn(List.of(99L));
    assertThat(service.index()).isEqualTo(new CatalogIndexService.IndexResult(0, 1, 3));
    verify(gemini, never()).products(anyList());
    verify(qdrant).delete(List.of(99L));
  }
}
