package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.access.AuthenticatedUser;
import dev.hoangtuan.shopflow.dto.access.GoogleLinkRequest;
import dev.hoangtuan.shopflow.exception.access.InvalidCodeException;
import dev.hoangtuan.shopflow.security.AccountPrincipal;
import dev.hoangtuan.shopflow.security.GoogleFlowSession;
import dev.hoangtuan.shopflow.service.AuthService;
import dev.hoangtuan.shopflow.service.GoogleAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
public class GoogleLinkController {
  private final GoogleAccountService accounts;
  private final AuthService auth;
  private final Clock clock;

  GoogleLinkController(GoogleAccountService accounts, AuthService auth, Clock clock) {
    this.accounts = accounts;
    this.auth = auth;
    this.clock = clock;
  }

  @PostMapping("/auth/google/link")
  AuthenticatedUser link(
      @Valid @RequestBody GoogleLinkRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var proof = GoogleFlowSession.pending(request, clock.instant());
    if (proof == null) throw new InvalidCodeException();
    var account =
        accounts.link(
            proof.subject(), proof.email(), proof.userId(), proof.authVersion(), body.password());
    AuthenticatedUser user =
        auth.establishSession(
            new AccountPrincipal(
                account.id(),
                account.username(),
                null,
                account.displayName(),
                account.role(),
                account.authVersion()),
            request,
            response);
    response.setHeader("Location", proof.returnTo());
    return user;
  }
}
