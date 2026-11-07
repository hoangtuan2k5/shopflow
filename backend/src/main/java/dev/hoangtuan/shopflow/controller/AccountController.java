package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.access.AccountProfile;
import dev.hoangtuan.shopflow.dto.access.ChallengeResponse;
import dev.hoangtuan.shopflow.dto.access.ChangePasswordRequest;
import dev.hoangtuan.shopflow.dto.access.ResendOtpRequest;
import dev.hoangtuan.shopflow.dto.access.ResetPasswordRequest;
import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import dev.hoangtuan.shopflow.service.AccountSelfService;
import dev.hoangtuan.shopflow.service.AuthService;
import dev.hoangtuan.shopflow.service.CustomerOtpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account")
@RestController
@RequestMapping("/auth/account")
public class AccountController {
  private final AccountSelfService accounts;
  private final AuthService auth;
  private final CustomerOtpService otp;

  public AccountController(AccountSelfService accounts, AuthService auth, CustomerOtpService otp) {
    this.otp = otp;
    this.accounts = accounts;
    this.auth = auth;
  }

  @PostMapping("/password/setup")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ChallengeResponse setup(
      @RequestBody(required = false) Map<String, Object> body, HttpServletRequest http) {
    if (body != null && !body.isEmpty())
      throw new CredentialsValidationException(Map.of("request", "No account fields are accepted"));
    var p = auth.currentPrincipal();
    return otp.setup(
        p.getUserId(), p.getAuthVersion(), null, http.getSession().getId(), http.getRemoteAddr());
  }

  @PostMapping("/password/setup/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ChallengeResponse resend(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest http) {
    var p = auth.currentPrincipal();
    return otp.setup(
        p.getUserId(),
        p.getAuthVersion(),
        request.challengeId(),
        http.getSession().getId(),
        http.getRemoteAddr());
  }

  @PostMapping("/password/setup/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void confirm(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
    var p = auth.currentPrincipal();
    otp.confirmSetup(
        p.getUserId(),
        p.getAuthVersion(),
        request.challengeId(),
        request.otp(),
        request.newPassword(),
        http.getSession().getId(),
        http.getRemoteAddr());
    auth.logout(http);
  }

  @PostMapping("/password/change")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
    var p = auth.currentPrincipal();
    accounts.changePassword(
        p.getUserId(), p.getAuthVersion(), request.currentPassword(), request.newPassword());
    auth.logout(http);
  }

  @GetMapping
  AccountProfile profile() {
    var principal = auth.currentPrincipal();
    return accounts.profile(principal.getUserId(), principal.getAuthVersion());
  }
}
