package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.access.AccountProfile;
import dev.hoangtuan.shopflow.dto.access.DeliveryDetails;
import dev.hoangtuan.shopflow.dto.access.DeliveryProfile;
import dev.hoangtuan.shopflow.exception.access.AccountActionException;
import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import dev.hoangtuan.shopflow.repository.jdbc.AccountSelfServiceRepository;
import dev.hoangtuan.shopflow.repository.jdbc.AccountSelfServiceRepository.Account;
import dev.hoangtuan.shopflow.security.CustomerCredentials;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountSelfService {
  private final AccountSelfServiceRepository repository;

  private final TransactionTemplate transactions;
  private final PasswordEncoder encoder;
  private final LoginRateLimiter limits;
  private final Clock clock;
  private final CustomerOtpService otp;

  public AccountSelfService(
      AccountSelfServiceRepository repository,
      TransactionTemplate transactions,
      PasswordEncoder encoder,
      LoginRateLimiter limits,
      Clock clock,
      CustomerOtpService otp) {
    this.transactions = transactions;
    this.encoder = encoder;
    this.limits = limits;
    this.clock = clock;
    this.otp = otp;
    this.repository = repository;
  }

  public AccountProfile profile(long id, long version) {
    Account account = requireCurrent(repository.find(id, false), version);
    boolean eligible = account.eligible();
    return new AccountProfile(
        account.displayName(),
        account.email(),
        new AccountProfile.Capabilities(
            eligible,
            eligible && account.passwordHash() != null,
            eligible && account.google() && account.passwordHash() == null));
  }

  public DeliveryProfile delivery(long id, long version) {
    return transactions.execute(
        tx -> {
          requireDelivery(id, version);
          return new DeliveryProfile(repository.delivery(id));
        });
  }

  public DeliveryProfile saveDelivery(long id, long version, DeliveryDetails details) {
    return transactions.execute(
        tx -> {
          requireDelivery(id, version);
          repository.saveDelivery(id, details, clock.instant());
          return new DeliveryProfile(repository.delivery(id));
        });
  }

  public void deleteDelivery(long id, long version) {
    transactions.executeWithoutResult(
        tx -> {
          requireDelivery(id, version);
          repository.deleteDelivery(id);
        });
  }

  private void requireDelivery(long id, long version) {
    Account account = requireCurrent(repository.find(id, true), version);
    if (!account.eligible())
      throw new AccountActionException(HttpStatus.FORBIDDEN, "Delivery settings are not available");
  }

  public void changePassword(long id, long version, String currentPassword, String newPassword) {
    if (!CustomerCredentials.validNewPassword(newPassword))
      throw new CredentialsValidationException(
          Map.of("newPassword", "Password does not meet the password policy"));
    String key = "account-password:" + id;
    limits.acquire(key);
    String email =
        transactions.execute(
            tx -> {
              Account account = requireCurrent(repository.find(id, true), version);
              if (!account.eligible() || account.passwordHash() == null)
                throw new AccountActionException(
                    HttpStatus.FORBIDDEN, "Password change is not available");
              if (currentPassword == null
                  || currentPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                  || !encoder.matches(currentPassword, account.passwordHash()))
                throw new CredentialsValidationException(
                    Map.of("currentPassword", "The current password is incorrect"));
              repository.changePassword(id, encoder.encode(newPassword), clock.instant());
              return account.email();
            });
    limits.clear(key);
    limits.clear(email);
    otp.notifyPasswordChanged(email, java.util.UUID.randomUUID());
  }

  static Account requireCurrent(Account account, long version) {
    if (account == null || !account.active() || account.authVersion() != version)
      throw new BadCredentialsException("Account session is no longer valid");
    return account;
  }
}
