package dev.hoangtuan.shopflow.search;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "shopflow.gemini.api-key=")
@AutoConfigureMockMvc
class CatalogIndexControllerTests {
  @Autowired MockMvc mvc;
  @MockitoBean QdrantClient qdrant;

  @Test
  void missingKeyReturns503BeforeQdrantIsTouched() throws Exception {
    mvc.perform(post("/catalog-index").with(csrf()).with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("GEMINI_API_KEY is not configured"));
    mvc.perform(
            get("/catalog-index/search")
                .param("q", "filter")
                .with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isServiceUnavailable());
    verifyNoInteractions(qdrant);
  }

  @Test
  void invalidQueriesReturn400WithoutCallingQdrant() throws Exception {
    for (String query : new String[] {"", "   "}) {
      mvc.perform(
              get("/catalog-index/search")
                  .param("q", query)
                  .with(user("owner").roles("SHOP_OWNER")))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(get("/catalog-index/search").with(user("owner").roles("SHOP_OWNER")))
        .andExpect(status().isBadRequest());
    for (String limit : new String[] {"0", "21", "no", "1.5"}) {
      mvc.perform(
              get("/catalog-index/search")
                  .param("q", "filter")
                  .param("limit", limit)
                  .with(user("owner").roles("SHOP_OWNER")))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(qdrant);
  }
}
