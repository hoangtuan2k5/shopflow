package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.integration.ai.GeminiEmbeddingClient;
import dev.hoangtuan.shopflow.integration.search.QdrantClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CatalogIndexIntegrationTests {
  @Container
  static final GenericContainer<?> QDRANT =
      new GenericContainer<>("qdrant/qdrant:v1.18.3")
          .withEnv("QDRANT__SERVICE__API_KEY", "test-qdrant-key")
          .withExposedPorts(6333);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "shopflow.qdrant.url",
        () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    registry.add("shopflow.qdrant.api-key", () -> "test-qdrant-key");
    registry.add("shopflow.qdrant.dimensions", () -> 3);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired QdrantClient qdrant;
  @MockitoBean GeminiEmbeddingClient gemini;

  @Test
  void realQdrantReindexIsIdempotentAndRemovesInactiveAndDeletedProducts() throws Exception {
    jdbc.update("DELETE FROM shopflow.stock_movements");
    jdbc.update("DELETE FROM shopflow.order_items");
    jdbc.update("DELETE FROM shopflow.inventory_items");
    jdbc.update("DELETE FROM shopflow.products");
    jdbc.update(
        "INSERT INTO shopflow.products(id,name,description,price,active) VALUES (10001,'HEPA','fine dust',100,TRUE),(10002,'Carbon','odor',100,TRUE)");
    when(gemini.products(anyList()))
        .thenAnswer(
            inv ->
                ((List<?>) inv.getArgument(0)).stream().map(p -> List.of(1.0, 0.0, 0.0)).toList());
    when(gemini.query("lọc bụi")).thenReturn(List.of(1.0, 0.0, 0.0));
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/catalog-index").with(csrf()).with(user("owner").roles("SHOP_OWNER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.indexed").value(2))
          .andExpect(jsonPath("$.removed").value(0));
      assertThat(qdrant.ids()).containsExactlyInAnyOrder(10001L, 10002L);
    }
    jdbc.update("UPDATE shopflow.products SET name='New HEPA' WHERE id=10001");
    jdbc.update("UPDATE shopflow.products SET active=FALSE WHERE id=10002");
    mvc.perform(post("/catalog-index").with(csrf()).with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.removed").value(1));
    assertThat(qdrant.ids()).containsExactly(10001L);
    mvc.perform(
            get("/catalog-index/search")
                .param("q", "  lọc bụi  ")
                .param("limit", "1")
                .with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productId").value(10001))
        .andExpect(jsonPath("$[0].name").value("New HEPA"));
    clearInvocations(gemini);
    for (String role : new String[] {"CUSTOMER", "WAREHOUSE"}) {
      mvc.perform(post("/catalog-index").with(csrf()).with(user("staff").roles(role)))
          .andExpect(status().isForbidden());
      mvc.perform(get("/catalog-index/search").param("q", "filter").with(user("staff").roles(role)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(gemini);
    assertThat(qdrant.ids()).containsExactly(10001L);
    jdbc.update("DELETE FROM shopflow.products WHERE id=10001");
    mvc.perform(post("/catalog-index").with(csrf()).with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexed").value(0))
        .andExpect(jsonPath("$.removed").value(1));
    assertThat(qdrant.ids()).isEmpty();
  }
}
