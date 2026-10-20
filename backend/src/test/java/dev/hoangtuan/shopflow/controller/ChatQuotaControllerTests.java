package dev.hoangtuan.shopflow.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.integration.ai.GemmaClient;
import dev.hoangtuan.shopflow.service.CatalogSearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "shopflow.chat.daily-limit=0")
@AutoConfigureMockMvc
class ChatQuotaControllerTests {
  @Autowired MockMvc mvc;
  @MockitoBean CatalogSearch search;
  @MockitoBean GemmaClient gemma;

  @Test
  void exhaustedQuotaReturnsRetryAfterWithoutEmbeddingOrGeneration() throws Exception {
    mvc.perform(
            post("/chat/messages")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"filter\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429));
    verifyNoInteractions(search);
    verify(gemma).requireKey();
    verifyNoMoreInteractions(gemma);
  }
}
