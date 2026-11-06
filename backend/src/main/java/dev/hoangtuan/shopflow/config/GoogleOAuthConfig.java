package dev.hoangtuan.shopflow.config;

import dev.hoangtuan.shopflow.security.AccountPrincipal;
import dev.hoangtuan.shopflow.security.GoogleAuthorizationRequests;
import dev.hoangtuan.shopflow.security.GoogleFlowSession;
import dev.hoangtuan.shopflow.security.GoogleFlowSession.PendingLink;
import dev.hoangtuan.shopflow.service.AuthService;
import dev.hoangtuan.shopflow.service.GoogleAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration
public class GoogleOAuthConfig {
  private final String clientId;
  private final String secret;
  private final GoogleAccountService accounts;
  private final AuthService auth;
  private final Clock clock;
  private final Environment environment;

  public GoogleOAuthConfig(
      @Value("${shopflow.auth.google.client-id:}") String clientId,
      @Value("${shopflow.auth.google.client-secret:}") String secret,
      GoogleAccountService accounts,
      AuthService auth,
      Clock clock,
      Environment environment) {
    this.clientId = clientId;
    this.secret = secret;
    this.accounts = accounts;
    this.auth = auth;
    this.clock = clock;
    this.environment = environment;
  }

  @Bean
  ClientRegistrationRepository googleClientRegistrations() {
    return new InMemoryClientRegistrationRepository(
        CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId(clientId.isBlank() ? "disabled" : clientId)
            .clientSecret(secret.isBlank() ? "disabled" : secret)
            .scope("openid", "email", "profile")
            .build());
  }

  void configure(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
    var requests =
        new GoogleAuthorizationRequests(
            clients,
            clock,
            !clientId.isBlank() && !secret.isBlank(),
            !environment.acceptsProfiles(Profiles.of("staging")));
    http.addFilterBefore(requests, OAuth2AuthorizationRequestRedirectFilter.class)
        .oauth2Login(
            oauth ->
                oauth
                    .securityContextRepository(new NullSecurityContextRepository())
                    .clientRegistrationRepository(clients)
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint
                                .authorizationRequestResolver(requests)
                                .authorizationRequestRepository(requests))
                    .authorizedClientService(
                        new OAuth2AuthorizedClientService() {
                          @Override
                          public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                              String id, String name) {
                            return null;
                          }

                          @Override
                          public void saveAuthorizedClient(
                              OAuth2AuthorizedClient client, Authentication principal) {}

                          @Override
                          public void removeAuthorizedClient(String id, String name) {}
                        })
                    .successHandler(this::success)
                    .failureHandler((request, response, failure) -> failed(request, response)));
  }

  private void success(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws java.io.IOException {
    anonymous(request);
    try {
      OidcUser user = (OidcUser) authentication.getPrincipal();
      if (!Boolean.TRUE.equals(user.getEmailVerified())) throw new IllegalArgumentException();
      String returnTo = GoogleFlowSession.returnTo(request, clock.instant());
      if (returnTo == null) throw new IllegalArgumentException();
      var account = accounts.resolve(user.getSubject(), user.getEmail(), user.getFullName());
      if (account.subject() == null) {
        GoogleFlowSession.pending(
            request,
            new PendingLink(
                user.getSubject(),
                account.email(),
                account.id(),
                account.authVersion(),
                returnTo,
                clock.instant().plusSeconds(300)));
        redirect(response, "/login?google=link_required");
      } else {
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
        redirect(response, returnTo);
      }
    } catch (org.springframework.security.core.AuthenticationException
        | dev.hoangtuan.shopflow.exception.access.CredentialsValidationException
        | IllegalArgumentException failure) {
      failed(request, response);
    }
  }

  private void anonymous(HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    var session = request.getSession(false);
    if (session != null)
      session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  }

  private void failed(HttpServletRequest request, HttpServletResponse response)
      throws java.io.IOException {
    anonymous(request);
    GoogleFlowSession.clearLink(request);
    redirect(response, "/login?google=failed");
  }

  private void redirect(HttpServletResponse response, String path) {
    response.setStatus(303);
    response.setHeader("Location", path);
  }
}
