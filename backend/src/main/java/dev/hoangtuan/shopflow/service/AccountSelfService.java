package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.access.AccountProfile;
import dev.hoangtuan.shopflow.repository.jdbc.AccountSelfServiceRepository;
import dev.hoangtuan.shopflow.repository.jdbc.AccountSelfServiceRepository.Account;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class AccountSelfService {
  private final AccountSelfServiceRepository repository;

  public AccountSelfService(AccountSelfServiceRepository repository) {
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

  static Account requireCurrent(Account account, long version) {
    if (account == null || !account.active() || account.authVersion() != version)
      throw new BadCredentialsException("Account session is no longer valid");
    return account;
  }
}
