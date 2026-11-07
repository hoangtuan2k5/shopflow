package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.access.AccountProfile;
import dev.hoangtuan.shopflow.service.AccountSelfService;
import dev.hoangtuan.shopflow.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account")
@RestController
@RequestMapping("/auth/account")
public class AccountController {
  private final AccountSelfService accounts;
  private final AuthService auth;

  public AccountController(AccountSelfService accounts, AuthService auth) {
    this.accounts = accounts;
    this.auth = auth;
  }

  @GetMapping
  AccountProfile profile() {
    var principal = auth.currentPrincipal();
    return accounts.profile(principal.getUserId(), principal.getAuthVersion());
  }
}
