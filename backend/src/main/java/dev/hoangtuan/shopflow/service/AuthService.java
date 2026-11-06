package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.access.AuthenticatedUser;
import dev.hoangtuan.shopflow.dto.access.LoginRequest;
import dev.hoangtuan.shopflow.dto.access.SessionResponse;
import dev.hoangtuan.shopflow.security.AccountPrincipal;
import dev.hoangtuan.shopflow.security.CustomerCredentials;
import dev.hoangtuan.shopflow.security.GoogleFlowSession;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final LoginRateLimiter loginRateLimiter;
  private final Clock clock;

  public AuthService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      LoginRateLimiter loginRateLimiter,
      Clock clock) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.loginRateLimiter = loginRateLimiter;
    this.clock = clock;
  }

  /**
   * Authenticates credentials and establishes a server-side session.
   *
   * <p><b>Contract:</b> reserves one rate-limit slot before authentication; rejection keeps the
   * slot, while success clears every slot for the username.
   *
   * @param request validated credentials
   * @param httpRequest current servlet request
   * @param httpResponse current servlet response
   * @return authenticated account exposed to the client
   * @throws AuthenticationException if the credentials are rejected
   * @throws LoginRateLimiter.RateLimitExceededException if the username has no attempt available
   */
  public AuthenticatedUser login(
      LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    loginRateLimiter.acquire(request.username());
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BadCredentialsException("Invalid username or password");
    }
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                CustomerCredentials.identifier(request.username()), request.password()));
    loginRateLimiter.clear(request.username());

    String returnTo = GoogleFlowSession.returnTo(httpRequest, clock.instant());
    AuthenticatedUser user =
        establishSession(principalOf(authentication), httpRequest, httpResponse);
    if (returnTo != null) httpResponse.setHeader("Location", returnTo);
    return user;
  }

  public AuthenticatedUser establishSession(
      AccountPrincipal principal,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());

    // Đổi session id sau khi xác thực để chặn session fixation.
    HttpSession existingSession = httpRequest.getSession(false);
    if (existingSession != null) {
      existingSession.invalidate();
    }
    httpRequest.getSession(true);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);
    org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()
        .saveToken(null, httpRequest, httpResponse);

    return principalOf(authentication).toAuthenticatedUser();
  }

  public void logout(HttpServletRequest httpRequest) {
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  public SessionResponse currentSession() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal)) {
      return SessionResponse.anonymous();
    }
    return SessionResponse.of(principalOf(authentication).toAuthenticatedUser());
  }

  private AccountPrincipal principalOf(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
      throw new org.springframework.security.authentication
          .AuthenticationCredentialsNotFoundException("Sign in required");
    }
    return principal;
  }

  public long currentUserId() {
    return principalOf(SecurityContextHolder.getContext().getAuthentication()).getUserId();
  }
}
