package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.access.ChallengeResponse;
import dev.hoangtuan.shopflow.dto.access.RegistrationRequest;
import dev.hoangtuan.shopflow.exception.access.AccountActionException;
import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import dev.hoangtuan.shopflow.exception.access.InvalidCodeException;
import dev.hoangtuan.shopflow.exception.access.OtpRateLimitException;
import dev.hoangtuan.shopflow.integration.email.ResendEmailClient;
import dev.hoangtuan.shopflow.repository.jdbc.AccountSelfServiceRepository;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository.Account;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository.Bucket;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository.Challenge;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository.Rate;
import dev.hoangtuan.shopflow.security.CustomerCredentials;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import dev.hoangtuan.shopflow.security.OtpVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CustomerOtpService {
  private static final String REGISTER = "REGISTER";
  private static final String RESET = "RESET_PASSWORD";
  private static final String SET = "SET_PASSWORD";
  private final AccountSelfServiceRepository accounts;
  private final EmailChallengeRepository repository;
  private final TransactionTemplate transactions;
  private final PasswordEncoder encoder;
  private final OtpVerifier verifier;
  private final ResendEmailClient mail;
  private final Clock clock;
  private final LoginRateLimiter loginLimits;

  public CustomerOtpService(
      EmailChallengeRepository repository,
      TransactionTemplate transactions,
      PasswordEncoder encoder,
      OtpVerifier verifier,
      ResendEmailClient mail,
      Clock clock,
      LoginRateLimiter loginLimits,
      AccountSelfServiceRepository accounts) {
    this.accounts = accounts;
    this.repository = repository;
    this.transactions = transactions;
    this.encoder = encoder;
    this.verifier = verifier;
    this.mail = mail;
    this.clock = clock;
    this.loginLimits = loginLimits;
  }

  public ChallengeResponse register(RegistrationRequest request, String sessionId, String ip) {
    String email = CustomerCredentials.email(request.email());
    String name = CustomerCredentials.registrationName(request.password(), request.displayName());
    requireConfigured();
    long started = System.nanoTime();
    try {
      String hash = encoder.encode(request.password());
      return deliver(
          transactions.execute(tx -> prepare(email, REGISTER, name, hash, null, sessionId, ip)));
    } finally {
      responseFloor(started);
    }
  }

  public ChallengeResponse forgot(String rawEmail, String sessionId, String ip) {
    String email = CustomerCredentials.email(rawEmail);
    requireConfigured();
    long started = System.nanoTime();
    try {
      return deliver(
          transactions.execute(tx -> prepare(email, RESET, null, null, null, sessionId, ip)));
    } finally {
      responseFloor(started);
    }
  }

  public ChallengeResponse resend(UUID id, String sessionId, String ip) {
    requireConfigured();
    Challenge previous = repository.find(id);
    if (previous == null || SET.equals(previous.purpose())) {
      countInvalidRequest(ip, "IP_SEND", 10);
      throw new InvalidCodeException();
    }
    long started = System.nanoTime();
    try {
      return deliver(
          transactions.execute(
              tx -> prepare(previous.email(), previous.purpose(), null, null, id, sessionId, ip)));
    } finally {
      responseFloor(started);
    }
  }

  public ChallengeResponse setup(
      long userId, long version, UUID previousId, String sessionId, String ip) {
    requireConfigured();
    var account = requireSetup(userId, version, false);
    if (previousId != null) {
      var previous = repository.find(previousId);
      if (previous == null
          || !SET.equals(previous.purpose())
          || !java.util.Objects.equals(previous.userId(), userId)) {
        countInvalidRequest(ip, "IP_SEND", 10);
        throw new InvalidCodeException();
      }
    }
    return deliver(
        transactions.execute(
            tx ->
                prepare(
                    account.email(), SET, null, null, previousId, sessionId, ip, userId, version)));
  }

  public void confirmSetup(
      long userId,
      long version,
      UUID id,
      String otp,
      String password,
      String sessionId,
      String ip) {
    requireSetup(userId, version, false);
    if (!CustomerCredentials.validNewPassword(password))
      throw new CredentialsValidationException(
          Map.of("newPassword", "Password does not meet the password policy"));
    String email = verifyProof(id, otp, sessionId, ip, SET, password, userId, version);
    loginLimits.clear(email);
    notifyPasswordChanged(email, id);
  }

  public void notifyPasswordChanged(String email, UUID id) {
    try {
      boolean reserved =
          Boolean.TRUE.equals(
              transactions.execute(
                  tx -> {
                    Instant now = clock.instant();
                    String day = now.atOffset(ZoneOffset.UTC).toLocalDate().toString();
                    Bucket bucket =
                        repository
                            .lockRates(List.of(rate("MAIL_DAILY", day, 90, now)), now)
                            .get("MAIL_DAILY");
                    if (bucket.count() >= bucket.limit()) return false;
                    repository.increment(bucket);
                    return true;
                  }));
      if (reserved) mail.sendPasswordChanged(email, repository.displayName(email), id);
    } catch (EmailUnavailableException | org.springframework.dao.DataAccessException ignored) {
      // Notification failure must not undo an already committed password change.
    }
  }

  private AccountSelfServiceRepository.Account requireSetup(long id, long version, boolean lock) {
    var account = AccountSelfService.requireCurrent(accounts.find(id, lock), version);
    if (!account.eligible() || !account.google())
      throw new AccountActionException(HttpStatus.FORBIDDEN, "Password setup is not available");
    if (account.passwordHash() != null)
      throw new AccountActionException(HttpStatus.CONFLICT, "A password is already configured");
    return account;
  }

  private Issue prepare(
      String email,
      String purpose,
      String name,
      String hash,
      UUID previousId,
      String sessionId,
      String ip) {
    return prepare(email, purpose, name, hash, previousId, sessionId, ip, null, null);
  }

  private Issue prepare(
      String email,
      String purpose,
      String name,
      String hash,
      UUID previousId,
      String sessionId,
      String ip,
      Long userId,
      Long version) {
    Instant now = clock.instant();
    var buckets =
        repository.lockRates(
            List.of(
                rate("EMAIL_SEND", email + "\0" + purpose, 3, now),
                rate("IP_SEND", ip, 10, now),
                rate("MAIL_DAILY", now.atOffset(ZoneOffset.UTC).toLocalDate().toString(), 90, now)),
            now);
    check(buckets.get("IP_SEND"), now);
    repository.lockEmail(email);
    Account account = repository.lockAccount(email);
    if (SET.equals(purpose)) requireSetup(userId, version, true);
    Challenge old = repository.lockChallenge(email, purpose);
    if (previousId != null
        && (!bound(old, previousId, sessionId)
            || !old.expiresAt().isAfter(now)
            || old.status().equals("CONSUMED")
            || old.status().equals("EXPIRED"))) {
      repository.increment(buckets.get("IP_SEND"));
      if (old != null && !old.expiresAt().isAfter(now)) repository.expire(old.id());
      return new Issue(null, null, purpose, null, null, new InvalidCodeException());
    }
    if (old != null && !old.expiresAt().isAfter(now)) repository.expire(old.id());
    if (old != null
        && old.status().equals("PENDING_SEND")
        && !old.issuedAt().plusSeconds(3).isAfter(now)) repository.delivery(old.id(), false);
    if (old != null && old.resendAt().isAfter(now))
      return new Issue(
          null, null, purpose, null, null, new OtpRateLimitException(now, old.resendAt()));
    for (Bucket bucket : buckets.values()) check(bucket, now);
    for (Bucket bucket : buckets.values()) repository.increment(bucket);
    UUID id = UUID.randomUUID();
    String otp = verifier.newCode();
    if (previousId != null) {
      name = old.displayName();
      hash = old.passwordHash();
    }
    boolean synthetic =
        REGISTER.equals(purpose)
            ? account != null
            : !SET.equals(purpose) && (account == null || !account.recoverable());
    if (previousId != null)
      synthetic |=
          old.synthetic()
              || (RESET.equals(purpose)
                  && !java.util.Objects.equals(
                      old.userId(), account == null ? null : account.id()));
    repository.replace(
        email,
        purpose,
        synthetic || REGISTER.equals(purpose) ? null : account.id(),
        id,
        verifier.sessionBinding(sessionId),
        verifier.code(email, purpose, id, otp),
        synthetic,
        hash,
        name,
        now,
        version);
    return new Issue(
        id,
        email,
        purpose,
        synthetic ? null : REGISTER.equals(purpose) ? name : account.displayName(),
        synthetic ? null : otp,
        null);
  }

  private ChallengeResponse deliver(Issue issue) {
    if (issue.error() != null) throw issue.error();
    if (issue.otp() != null) {
      boolean accepted = false;
      try {
        if (REGISTER.equals(issue.purpose()))
          mail.sendRegistrationCode(issue.email(), issue.name(), issue.otp(), issue.id());
        else if (SET.equals(issue.purpose()))
          mail.sendSetupCode(issue.email(), issue.name(), issue.otp(), issue.id());
        else mail.sendResetCode(issue.email(), issue.name(), issue.otp(), issue.id());
        accepted = true;
      } catch (EmailUnavailableException ignored) {
        // Delivery failure and synthetic challenges share the public response.
      }
      boolean success = accepted;
      transactions.executeWithoutResult(tx -> repository.delivery(issue.id(), success));
      if (SET.equals(issue.purpose()) && !accepted) throw new EmailUnavailableException();
    }
    transactions.executeWithoutResult(tx -> repository.cleanup(clock.instant()));
    return ChallengeResponse.issued(issue.id());
  }

  public void verify(UUID id, String otp, String sessionId, String ip) {
    verifyProof(id, otp, sessionId, ip, REGISTER, null);
  }

  public void reset(UUID id, String otp, String newPassword, String sessionId, String ip) {
    if (!CustomerCredentials.validNewPassword(newPassword))
      throw new CredentialsValidationException(
          Map.of(
              "newPassword",
              "Use at least 8 characters with uppercase, lowercase, a number and a symbol; maximum 72 UTF-8 bytes"));
    String email = verifyProof(id, otp, sessionId, ip, RESET, newPassword);
    loginLimits.clear(email);
    notifyPasswordChanged(email, id);
  }

  private String verifyProof(
      UUID id, String otp, String sessionId, String ip, String purpose, String newPassword) {
    return verifyProof(id, otp, sessionId, ip, purpose, newPassword, null, null);
  }

  private String verifyProof(
      UUID id,
      String otp,
      String sessionId,
      String ip,
      String purpose,
      String newPassword,
      Long userId,
      Long version) {
    verifier.requireConfigured();
    Challenge lookup = repository.find(id);
    if (SET.equals(purpose)
        && (lookup == null
            || !SET.equals(lookup.purpose())
            || !java.util.Objects.equals(lookup.userId(), userId))) {
      countInvalidRequest(ip, "IP_VERIFY", 30);
      throw new InvalidCodeException();
    }
    VerifyResult result;
    try {
      result =
          transactions.execute(
              tx -> {
                Instant now = clock.instant();
                var limits = new java.util.ArrayList<Rate>();
                limits.add(rate("IP_VERIFY", ip, 30, now));
                if (lookup != null && lookup.purpose().equals(purpose))
                  limits.add(rate("EMAIL_VERIFY_FAILURE", lookup.email() + "\0" + purpose, 5, now));
                var buckets = repository.lockRates(limits, now);
                check(buckets.get("IP_VERIFY"), now);
                repository.increment(buckets.get("IP_VERIFY"));
                if (lookup == null || !lookup.purpose().equals(purpose))
                  return new VerifyResult(false, null);
                repository.lockEmail(lookup.email());
                Account account = repository.lockAccount(lookup.email());
                Challenge current = repository.lockChallenge(lookup.email(), purpose);
                if (SET.equals(purpose)) {
                  requireSetup(userId, version, true);
                  if (current == null
                      || !java.util.Objects.equals(current.userId(), userId)
                      || !java.util.Objects.equals(current.issuedAuthVersion(), version))
                    return new VerifyResult(false, null);
                }
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
                        current.verifier(), verifier.code(current.email(), purpose, id, otp));
                if (!matches || current.synthetic() || !current.status().equals("ISSUED")) {
                  repository.failedAttempt(id);
                  repository.increment(failures);
                  return new VerifyResult(
                      false,
                      current.failedAttempts() + 1 >= 5 || failures.count() + 1 >= 5
                          ? failures.expiresAt()
                          : null);
                }
                if (REGISTER.equals(purpose)) {
                  if (account != null) {
                    repository.expire(id);
                    return new VerifyResult(false, null);
                  }
                  repository.activate(current, now);
                } else {
                  if (account == null
                      || (!SET.equals(purpose) && !account.recoverable())
                      || !java.util.Objects.equals(current.userId(), account.id())) {
                    repository.expire(id);
                    return new VerifyResult(false, null);
                  }
                  repository.resetPassword(current, encoder.encode(newPassword), now);
                }
                return new VerifyResult(true, null);
              });
    } catch (org.springframework.dao.DuplicateKeyException collision) {
      countInvalidRequest(ip, "IP_VERIFY", 30);
      throw new InvalidCodeException();
    }
    if (result.retryAt() != null)
      throw new OtpRateLimitException(clock.instant(), result.retryAt());
    if (!result.success()) throw new InvalidCodeException();
    return lookup.email();
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

  private record Issue(
      UUID id, String email, String purpose, String name, String otp, RuntimeException error) {}

  private record VerifyResult(boolean success, Instant retryAt) {}
}
