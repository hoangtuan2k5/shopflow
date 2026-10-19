package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.catalog.ProductDto;
import dev.hoangtuan.shopflow.dto.chat.ChatRequest;
import dev.hoangtuan.shopflow.dto.chat.ChatResponse;
import dev.hoangtuan.shopflow.exception.chat.ChatUnavailableException;
import dev.hoangtuan.shopflow.integration.ai.GemmaClient;
import dev.hoangtuan.shopflow.model.chat.ModelReply;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Ground model suggestions in active catalog candidates.
 *
 * <p><b>Contract:</b> response products are active, whitelisted and hydrated from the database.
 *
 * <p><b>Non-obvious behavior:</b> free-form prose is model output, not a compatibility guarantee.
 */
@Service
public class ChatService {
  private final CatalogSearch search;
  private final CatalogReader catalog;
  private final GemmaClient gemma;
  private final ChatQuotaGuard quota;
  private final String primary;
  private final String fallback;
  private final double scoreFloor;

  public ChatService(
      CatalogSearch search,
      CatalogReader catalog,
      GemmaClient gemma,
      ChatQuotaGuard quota,
      @Value("${shopflow.chat.model}") String primary,
      @Value("${shopflow.chat.fallback-model}") String fallback,
      @Value("${shopflow.chat.score-floor}") double scoreFloor) {
    this.search = search;
    this.catalog = catalog;
    this.gemma = gemma;
    this.quota = quota;
    this.primary = primary;
    this.fallback = fallback;
    this.scoreFloor = scoreFloor;
  }

  public ChatResponse send(ChatRequest request) {
    long deadline = System.nanoTime() + Duration.ofSeconds(18).toNanos();
    gemma.requireKey();
    quota.take();
    var matches = search.search(request.question());
    var ids =
        matches.stream()
            .filter(m -> m.score() >= scoreFloor)
            .map(CatalogSearch.Match::productId)
            .toList();
    var candidates = catalog.findActiveByIds(ids);
    quota.take();
    ModelReply answer;
    try {
      answer = gemma.generate(primary, request, candidates, remaining(deadline, 6));
    } catch (ChatUnavailableException e) {
      var time = remaining(deadline, 10);
      quota.take();
      answer = gemma.generate(fallback, request, candidates, time);
    }
    var allowed = candidates.stream().map(ProductDto.Detail::id).collect(Collectors.toSet());
    var chosen =
        answer.productIds().stream().filter(allowed::contains).distinct().limit(3).toList();
    var current =
        catalog.findActiveByIds(chosen).stream()
            .collect(Collectors.toMap(ProductDto.Detail::id, Function.identity()));
    var products =
        chosen.stream()
            .filter(current::containsKey)
            .map(current::get)
            .map(
                p ->
                    new ProductDto.ListItem(
                        p.id(), p.name(), p.price(), p.stockStatus(), p.imageUrl()))
            .toList();
    return new ChatResponse(answer.reply(), products);
  }

  private Duration remaining(long deadline, int seconds) {
    long nanos = deadline - System.nanoTime();
    if (nanos <= 0) throw new ChatUnavailableException();
    return Duration.ofNanos(Math.min(nanos, Duration.ofSeconds(seconds).toNanos()));
  }
}
