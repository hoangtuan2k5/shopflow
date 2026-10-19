package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatQuotaGuardTests {
  @Test
  void atomicallyCapsAttemptsAndResetsAtVietnamMidnight() throws Exception {
    var clock = mock(Clock.class);
    when(clock.withZone(any())).thenAnswer(inv -> Clock.fixed(clock.instant(), inv.getArgument(0)));
    when(clock.instant()).thenReturn(Instant.parse("2026-10-18T16:59:00Z"));
    var guard = new ChatQuotaGuard(50, clock);
    var success = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(8)) {
      for (int i = 0; i < 100; i++)
        pool.submit(
            () -> {
              try {
                guard.take();
                success.incrementAndGet();
              } catch (ChatQuotaGuard.LimitReached ignored) {
              }
            });
    }
    assertThat(success.get()).isEqualTo(50);
    assertThatThrownBy(guard::take)
        .isInstanceOfSatisfying(
            ChatQuotaGuard.LimitReached.class, e -> assertThat(e.retryAfter()).isEqualTo(60));
    when(clock.instant()).thenReturn(Instant.parse("2026-10-18T17:00:00Z"));
    guard.take();
  }
}
