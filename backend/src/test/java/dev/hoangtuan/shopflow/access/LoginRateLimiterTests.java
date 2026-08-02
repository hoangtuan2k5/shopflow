package dev.hoangtuan.shopflow.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTests {

  @Test
  void allowsAttemptsWhenTheFailureWindowExpires() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-29T00:00:00Z"));
    LoginRateLimiter limiter = new LoginRateLimiter(clock);

    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.acquire("keeper");
    }

    assertThatThrownBy(() -> limiter.acquire("keeper"))
        .isInstanceOf(LoginRateLimiter.RateLimitExceededException.class);

    clock.advance(Duration.ofMillis(500));

    assertThatThrownBy(() -> limiter.acquire("keeper"))
        .isInstanceOf(LoginRateLimiter.RateLimitExceededException.class)
        .satisfies(
            exception ->
                assertThat(
                        ((LoginRateLimiter.RateLimitExceededException) exception)
                            .retryAfterSeconds())
                    .isEqualTo(900));

    clock.advance(Duration.ofMinutes(15).minusMillis(500));

    assertThatCode(() -> limiter.acquire("keeper")).doesNotThrowAnyException();
  }

  @Test
  void reservesOnlyFiveAttemptsBeforeAuthenticationCompletes() {
    LoginRateLimiter limiter = new LoginRateLimiter();

    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.acquire("keeper");
    }

    assertThatThrownBy(() -> limiter.acquire("keeper"))
        .isInstanceOf(LoginRateLimiter.RateLimitExceededException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
