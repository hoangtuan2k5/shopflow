package dev.hoangtuan.shopflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.filter.OncePerRequestFilter;

public final class GoogleAuthorizationRequests extends OncePerRequestFilter
    implements OAuth2AuthorizationRequestResolver,
        AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
  private static final String START = "/oauth2/authorization/google";
  private static final String CALLBACK = "/login/oauth2/code/google";
  private static final String ISSUED = GoogleAuthorizationRequests.class.getName();
  private static final Map<String, String> PRODUCTION =
      Map.of(
          "shopflow.hoangtuan.dev", "https://shopflow.hoangtuan.dev",
          "shopflow.phamthuy.dev", "https://shopflow.phamthuy.dev");
  private static final Map<String, String> DEVELOPMENT =
      Map.of("localhost:5173", "http://localhost:5173", "127.0.0.1:5173", "http://127.0.0.1:5173");
  private final DefaultOAuth2AuthorizationRequestResolver resolver;
  private final HttpSessionOAuth2AuthorizationRequestRepository repository =
      new HttpSessionOAuth2AuthorizationRequestRepository();
  private final Clock clock;
  private final boolean enabled;
  private final boolean development;

  public GoogleAuthorizationRequests(
      ClientRegistrationRepository clients, Clock clock, boolean enabled, boolean development) {
    this.resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
    this.resolver.setAuthorizationRequestCustomizer(
        OAuth2AuthorizationRequestCustomizers.withPkce());
    this.clock = clock;
    this.enabled = enabled;
    this.development = development;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getServletPath().equals(START)
        || request.getServletPath().equals(CALLBACK)
        || request.getRequestURI().equals(START)
        || request.getRequestURI().equals(CALLBACK)) {
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("Referrer-Policy", "no-referrer");
      String returnTo = request.getParameter("returnTo");
      if (origin(request) == null
          || (request.getRequestURI().equals(START)
              && returnTo != null
              && !GoogleFlowSession.safeReturnTo(returnTo))) {
        response.setStatus(400);
        return;
      }
      if (!enabled) {
        response.setStatus(503);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private String origin(HttpServletRequest request) {
    String host = request.getHeader("Host");
    String origin = PRODUCTION.get(host == null ? "" : host);
    return origin == null && development ? DEVELOPMENT.get(host == null ? "" : host) : origin;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return customize(request, resolver.resolve(request));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String id) {
    return customize(request, resolver.resolve(request, id));
  }

  private OAuth2AuthorizationRequest customize(
      HttpServletRequest request, OAuth2AuthorizationRequest resolved) {
    if (resolved == null) return null;
    String returnTo = request.getParameter("returnTo");
    GoogleFlowSession.start(request, returnTo == null ? "/customer" : returnTo, clock.instant());
    return OAuth2AuthorizationRequest.from(resolved)
        .redirectUri(origin(request) + "/api" + CALLBACK)
        .build();
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorization,
      HttpServletRequest request,
      HttpServletResponse response) {
    repository.saveAuthorizationRequest(authorization, request, response);
    if (authorization != null) request.getSession().setAttribute(ISSUED, clock.instant());
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    var session = request.getSession(false);
    Object value = session == null ? null : session.getAttribute(ISSUED);
    OAuth2AuthorizationRequest saved = repository.loadAuthorizationRequest(request);
    return value instanceof Instant issued
            && issued.plusSeconds(300).isAfter(clock.instant())
            && saved != null
            && saved.getRedirectUri().equals(origin(request) + "/api" + CALLBACK)
        ? saved
        : null;
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest valid = loadAuthorizationRequest(request);
    repository.removeAuthorizationRequest(request, response);
    var session = request.getSession(false);
    if (session != null) session.removeAttribute(ISSUED);
    return valid;
  }
}
