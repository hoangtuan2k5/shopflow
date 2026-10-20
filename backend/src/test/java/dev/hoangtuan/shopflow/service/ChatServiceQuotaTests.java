package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.hoangtuan.shopflow.dto.chat.ChatRequest;
import dev.hoangtuan.shopflow.exception.chat.ChatUnavailableException;
import dev.hoangtuan.shopflow.integration.ai.GemmaClient;
import dev.hoangtuan.shopflow.model.chat.ModelReply;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatServiceQuotaTests {
  @Test
  void fallbackConsumesQuotaAndCannotExceedTheCap() {
    var search = mock(CatalogSearch.class);
    var catalog = mock(CatalogReader.class);
    var gemma = mock(GemmaClient.class);
    when(search.search(anyString())).thenReturn(List.of());
    when(catalog.findActiveByIds(anyList())).thenReturn(List.of());
    when(gemma.generate(anyString(), any(), anyList(), any(Duration.class)))
        .thenThrow(new ChatUnavailableException());
    var service =
        new ChatService(search, catalog, gemma, new ChatQuotaGuard(2), "primary", "fallback", 0.7);
    assertThatThrownBy(() -> service.send(new ChatRequest("filter", List.of())))
        .isInstanceOf(ChatQuotaGuard.LimitReached.class);
    verify(gemma)
        .generate(
            eq("primary"),
            any(),
            anyList(),
            argThat(t -> !t.isNegative() && t.compareTo(Duration.ofSeconds(6)) <= 0));
    verify(gemma, never()).generate(eq("fallback"), any(), anyList(), any(Duration.class));
  }

  @Test
  void fallbackCanUseRemainingTimeBeyondThePrimarySixSecondLimit() {
    var search = mock(CatalogSearch.class);
    var catalog = mock(CatalogReader.class);
    var gemma = mock(GemmaClient.class);
    when(search.search(anyString())).thenReturn(List.of());
    when(catalog.findActiveByIds(anyList())).thenReturn(List.of());
    when(gemma.generate(eq("primary"), any(), anyList(), any(Duration.class)))
        .thenThrow(new ChatUnavailableException());
    when(gemma.generate(eq("fallback"), any(), anyList(), any(Duration.class)))
        .thenReturn(new ModelReply("No matching products", List.of()));
    var service =
        new ChatService(search, catalog, gemma, new ChatQuotaGuard(3), "primary", "fallback", 0.7);
    assertThat(service.send(new ChatRequest("filter", List.of())).products()).isEmpty();
    verify(gemma)
        .generate(
            eq("fallback"),
            any(),
            anyList(),
            argThat(
                t ->
                    t.compareTo(Duration.ofSeconds(6)) > 0
                        && t.compareTo(Duration.ofSeconds(10)) <= 0));
  }
}
