package dev.hoangtuan.shopflow.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limit external chat AI attempts by the Vietnam business day.
 *
 * <p><b>Contract:</b> reserve each attempt atomically before external work.
 *
 * <p><b>Non-obvious behavior:</b> the counter resets after process restart.
 */
@Component
public class ChatQuotaGuard {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final Clock clock;
  private final int limit;
  private LocalDate date;
  private int calls;

  @org.springframework.beans.factory.annotation.Autowired
  public ChatQuotaGuard(@Value("${shopflow.chat.daily-limit}") int limit) {
    this(limit, Clock.systemUTC());
  }

  public ChatQuotaGuard(int limit, Clock clock) {
    this.limit = limit;
    this.clock = clock;
  }

  public synchronized void take() {
    var today = LocalDate.now(clock.withZone(ZONE));
    if (!today.equals(date)) {
      date = today;
      calls = 0;
    }
    if (calls >= limit) {
      long seconds =
          Duration.between(clock.instant(), today.plusDays(1).atStartOfDay(ZONE).toInstant())
              .getSeconds();
      throw new LimitReached(Math.max(1, seconds));
    }
    calls++;
  }

  // shortcut: one-instance memory counter; use shared persistent limits before adding replicas.
  public static class LimitReached extends RuntimeException {
    private final long retryAfter;

    LimitReached(long retryAfter) {
      this.retryAfter = retryAfter;
    }

    public long retryAfter() {
      return retryAfter;
    }
  }
}
