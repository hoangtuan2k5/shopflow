package dev.hoangtuan.shopflow.exception.access;

import java.time.Duration;
import java.time.Instant;

public class OtpRateLimitException extends RuntimeException {
  private final long retryAfterSeconds;

  public OtpRateLimitException(Instant now, Instant until) {
    this.retryAfterSeconds = Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
