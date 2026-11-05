package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.access.ChallengeResponse;
import dev.hoangtuan.shopflow.dto.access.RegistrationRequest;
import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import dev.hoangtuan.shopflow.exception.access.InvalidCodeException;
import dev.hoangtuan.shopflow.exception.access.OtpRateLimitException;
import dev.hoangtuan.shopflow.integration.email.ResendEmailClient;
import dev.hoangtuan.shopflow.repository.jdbc.RegistrationRepository;
import dev.hoangtuan.shopflow.repository.jdbc.RegistrationRepository.Bucket;
import dev.hoangtuan.shopflow.repository.jdbc.RegistrationRepository.Challenge;
import dev.hoangtuan.shopflow.repository.jdbc.RegistrationRepository.Rate;
import dev.hoangtuan.shopflow.security.CustomerCredentials;
import dev.hoangtuan.shopflow.security.OtpVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RegistrationService {
  private final RegistrationRepository repository;
  private final TransactionTemplate transactions;
  private final PasswordEncoder encoder;
  private final OtpVerifier verifier;
  private final ResendEmailClient mail;
  private final Clock clock;

  public RegistrationService(
      RegistrationRepository repository,
      TransactionTemplate transactions,
      PasswordEncoder encoder,
      OtpVerifier verifier,
      ResendEmailClient mail,
      Clock clock) {
    this.repository = repository;
    this.transactions = transactions;
    this.encoder = encoder;
    this.verifier = verifier;
    this.mail = mail;
    this.clock = clock;
  }

  public ChallengeResponse register(RegistrationRequest request, String sessionId, String ip) {
    String email = CustomerCredentials.email(request.email());
    String name = CustomerCredentials.registrationName(request.password(), request.displayName());
    requireConfigured();
    long started = System.nanoTime();
    try {
      String hash = encoder.encode(request.password());
      var result = transactions.execute(tx -> prepare(email, name, hash, null, sessionId, ip));
      return deliver(result);
    } finally {
      responseFloor(started);
    }
  }

  public ChallengeResponse resend(UUID id, String sessionId, String ip) {
    requireConfigured();
    Challenge previous = repository.find(id);
    if (previous == null || !"REGISTER".equals(previous.purpose())) {
      countInvalidRequest(ip, "IP_SEND", 10);
      throw new InvalidCodeException();
    }
    long started = System.nanoTime();
    try {
      var result =
          transactions.execute(tx -> prepare(previous.email(), null, null, id, sessionId, ip));
      return deliver(result);
    } finally {
      responseFloor(started);
    }
  }

  private Issue prepare(
      String email, String name, String hash, UUID previousId, String sessionId, String ip) {
    Instant now = clock.instant();
    var buckets =
        repository.lockRates(
            List.of(
                rate("EMAIL_SEND", email + "\0REGISTER", 3, now),
                rate("IP_SEND", ip, 10, now),
                rate("MAIL_DAILY", now.atOffset(ZoneOffset.UTC).toLocalDate().toString(), 90, now)),
            now);
    check(buckets.get("IP_SEND"), now);
    repository.lockEmail(email);
    boolean existing = repository.lockExistingUser(email);
    Challenge old = repository.lockChallenge(email, "REGISTER");
    if (previousId != null
        && (!bound(old, previousId, sessionId)
            || !old.expiresAt().isAfter(now)
            || old.status().equals("CONSUMED")
            || old.status().equals("EXPIRED"))) {
      repository.increment(buckets.get("IP_SEND"));
      if (old != null && !old.expiresAt().isAfter(now)) repository.expire(old.id());
      return new Issue(null, null, null, null, new InvalidCodeException());
    }
    if (old != null && !old.expiresAt().isAfter(now)) repository.expire(old.id());
    if (old != null
        && old.status().equals("PENDING_SEND")
        && !old.issuedAt().plusSeconds(3).isAfter(now)) {
      repository.delivery(old.id(), false);
    }
    if (old != null && old.resendAt().isAfter(now)) {
      return new Issue(null, null, null, null, new OtpRateLimitException(now, old.resendAt()));
    }
    for (Bucket bucket : buckets.values()) check(bucket, now);
    for (Bucket bucket : buckets.values()) repository.increment(bucket);
    UUID id = UUID.randomUUID();
    String otp = verifier.newCode();
    if (previousId != null) {
      name = old.displayName();
      hash = old.passwordHash();
    }
    boolean synthetic = existing || (previousId != null && old.synthetic());
    repository.replace(
        email,
        id,
        verifier.sessionBinding(sessionId),
        verifier.code(email, "REGISTER", id, otp),
        synthetic,
        hash,
        name,
        now);
    return new Issue(id, email, synthetic ? null : name, synthetic ? null : otp, null);
  }

  private ChallengeResponse deliver(Issue issue) {
    if (issue.error() != null) throw issue.error();
    if (issue.otp() != null) {
      boolean accepted = false;
      try {
        mail.sendRegistrationCode(issue.email(), issue.name(), issue.otp(), issue.id());
        accepted = true;
      } catch (EmailUnavailableException ignored) {
        // Same response for delivery failure and synthetic challenges prevents enumeration.
      }
      boolean success = accepted;
      transactions.executeWithoutResult(tx -> repository.delivery(issue.id(), success));
    }
    transactions.executeWithoutResult(tx -> repository.cleanup(clock.instant()));
    return ChallengeResponse.issued(issue.id());
  }

  public void verify(UUID id, String otp, String sessionId, String ip) {
    verifier.requireConfigured();
    Challenge lookup = repository.find(id);
    VerifyResult result;
    try {
      result =
          transactions.execute(
              tx -> {
                Instant now = clock.instant();
                var limits = new java.util.ArrayList<Rate>();
                limits.add(rate("IP_VERIFY", ip, 30, now));
                if (lookup != null && lookup.purpose().equals("REGISTER"))
                  limits.add(rate("EMAIL_VERIFY_FAILURE", lookup.email() + "\0REGISTER", 5, now));
                var buckets = repository.lockRates(limits, now);
                check(buckets.get("IP_VERIFY"), now);
                repository.increment(buckets.get("IP_VERIFY"));
                if (lookup == null || !lookup.purpose().equals("REGISTER"))
                  return new VerifyResult(false, null);
                repository.lockEmail(lookup.email());
                boolean existing = repository.lockExistingUser(lookup.email());
                Challenge current = repository.lockChallenge(lookup.email(), "REGISTER");
                if (!bound(current, id, sessionId)) return new VerifyResult(false, null);
                if (!current.expiresAt().isAfter(now)) {
                  repository.expire(id);
                  return new VerifyResult(false, null);
                }
                if (current.status().equals("PENDING_SEND")
                    && !current.issuedAt().plusSeconds(3).isAfter(now))
                  repository.delivery(id, false);
                if (!current.status().equals("ISSUED") && !current.status().equals("FAILED"))
                  return new VerifyResult(false, null);
                Bucket failures = buckets.get("EMAIL_VERIFY_FAILURE");
                if (failures.count() >= 5 || current.failedAttempts() >= 5)
                  return new VerifyResult(false, failures.expiresAt());
                boolean matches =
                    verifier.matches(
                        current.verifier(), verifier.code(current.email(), "REGISTER", id, otp));
                if (!matches || current.synthetic() || !current.status().equals("ISSUED")) {
                  repository.failedAttempt(id);
                  repository.increment(failures);
                  return new VerifyResult(
                      false,
                      current.failedAttempts() + 1 >= 5 || failures.count() + 1 >= 5
                          ? failures.expiresAt()
                          : null);
                }
                if (existing) {
                  repository.expire(id);
                  return new VerifyResult(false, null);
                }
                repository.activate(current, now);
                return new VerifyResult(true, null);
              });
    } catch (org.springframework.dao.DuplicateKeyException collision) {
      countInvalidRequest(ip, "IP_VERIFY", 30);
      throw new InvalidCodeException();
    }
    if (result.retryAt() != null)
      throw new OtpRateLimitException(clock.instant(), result.retryAt());
    if (!result.success()) throw new InvalidCodeException();
  }

  private boolean bound(Challenge challenge, UUID id, String sessionId) {
    return challenge != null
        && challenge.id().equals(id)
        && sessionId != null
        && verifier.matches(challenge.binding(), verifier.sessionBinding(sessionId));
  }

  private void countInvalidRequest(String ip, String scope, int max) {
    transactions.executeWithoutResult(
        tx -> {
          Instant now = clock.instant();
          Bucket bucket = repository.lockRates(List.of(rate(scope, ip, max, now)), now).get(scope);
          check(bucket, now);
          repository.increment(bucket);
        });
  }

  private Rate rate(String scope, String key, int max, Instant now) {
    Instant expires =
        scope.equals("MAIL_DAILY")
            ? now.atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
            : now.plusSeconds(900);
    return new Rate(scope, key, max, expires);
  }

  private void check(Bucket bucket, Instant now) {
    if (bucket.count() >= bucket.limit()) throw new OtpRateLimitException(now, bucket.expiresAt());
  }

  private void requireConfigured() {
    verifier.requireConfigured();
    mail.requireConfigured();
  }

  private void responseFloor(long started) {
    long remaining = TimeUnit.SECONDS.toNanos(4) - (System.nanoTime() - started);
    if (remaining > 0) {
      try {
        TimeUnit.NANOSECONDS.sleep(remaining);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new EmailUnavailableException();
      }
    }
  }

  private record Issue(UUID id, String email, String name, String otp, RuntimeException error) {}

  private record VerifyResult(boolean success, Instant retryAt) {}
}
