package dev.hoangtuan.shopflow.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

  private static final int MAX_FAILURES = 5;
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final int MAX_IDENTIFIERS = 10_000;

  private final Clock clock;

  // ponytail: one backend process; replace this bounded in-memory store with Redis when scaling
  // out.
  private final Map<String, FailureWindow> failures =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FailureWindow> eldest) {
          return size() > MAX_IDENTIFIERS;
        }
      };

  public LoginRateLimiter() {
    this(Clock.systemUTC());
  }

  public LoginRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public synchronized void acquire(String username) {
    String key = key(username);
    FailureWindow failureWindow = failures.get(key);
    Instant now = clock.instant();
    if (failureWindow == null || !failureWindow.expiresAt().isAfter(now)) {
      failures.put(key, new FailureWindow(1, now.plus(WINDOW)));
      return;
    }
    if (failureWindow.failures() >= MAX_FAILURES) {
      throw new RateLimitExceededException(Duration.between(now, failureWindow.expiresAt()));
    }
    failures.put(key, new FailureWindow(failureWindow.failures() + 1, failureWindow.expiresAt()));
  }

  public synchronized void clear(String username) {
    failures.remove(key(username));
  }

  private static String key(String username) {
    return username.toLowerCase(Locale.ROOT);
  }

  private record FailureWindow(int failures, Instant expiresAt) {}

  public static class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    RateLimitExceededException(Duration retryAfter) {
      this.retryAfterSeconds =
          Math.max(1, retryAfter.getSeconds() + (retryAfter.getNano() == 0 ? 0 : 1));
    }

    public long retryAfterSeconds() {
      return retryAfterSeconds;
    }
  }
}
