package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.exception.access.InvalidCodeException;
import dev.hoangtuan.shopflow.repository.jdbc.EmailChallengeRepository;
import dev.hoangtuan.shopflow.repository.jdbc.GoogleAccountRepository;
import dev.hoangtuan.shopflow.repository.jdbc.GoogleAccountRepository.GoogleAccount;
import dev.hoangtuan.shopflow.security.CustomerCredentials;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccountService {
  private final GoogleAccountRepository accounts;
  private final EmailChallengeRepository challenges;
  private final PasswordEncoder encoder;
  private final LoginRateLimiter limits;
  private final Clock clock;

  public GoogleAccountService(
      GoogleAccountRepository accounts,
      EmailChallengeRepository challenges,
      PasswordEncoder encoder,
      LoginRateLimiter limits,
      Clock clock) {
    this.accounts = accounts;
    this.challenges = challenges;
    this.encoder = encoder;
    this.limits = limits;
    this.clock = clock;
  }

  @Transactional
  public GoogleAccount resolve(String subject, String email, String name) {
    if (subject == null || subject.isBlank() || subject.length() > 255) throw rejected();
    email = CustomerCredentials.email(email);
    accounts.lockSubject(subject);
    challenges.lockEmail(email);
    GoogleAccount existing = accounts.lockBySubject(subject);
    if (existing != null) {
      if (!existing.active()) throw rejected();
      return existing;
    }
    existing = accounts.lockByEmail(email);
    if (existing != null) {
      if (!existing.active() || existing.passwordHash() == null || existing.subject() != null)
        throw rejected();
      return existing;
    }
    var pending = challenges.lockChallenge(email, "REGISTER");
    if (pending != null) challenges.expire(pending.id());
    String displayName = name == null || name.isBlank() ? "Google customer" : name.trim();
    displayName =
        displayName.substring(
            0,
            displayName.offsetByCodePoints(
                0, Math.min(255, displayName.codePointCount(0, displayName.length()))));
    return accounts.create(subject, email, displayName, clock.instant());
  }

  @Transactional
  public GoogleAccount link(
      String subject, String email, long userId, long version, String password) {
    limits.acquire(email);
    accounts.lockSubject(subject);
    challenges.lockEmail(email);
    GoogleAccount account = accounts.lockByEmail(email);
    if (account == null
        || account.id() != userId
        || account.authVersion() != version
        || !account.active()
        || account.passwordHash() == null
        || account.subject() != null
        || accounts.lockBySubject(subject) != null) throw new InvalidCodeException();
    if (password.getBytes(StandardCharsets.UTF_8).length > 72
        || !encoder.matches(password, account.passwordHash())) throw rejected();
    accounts.link(account.id(), subject, clock.instant());
    limits.clear(email);
    return account;
  }

  private BadCredentialsException rejected() {
    return new BadCredentialsException("Invalid username or password");
  }
}
