package dev.hoangtuan.shopflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.integration.email.ResendEmailClient;
import dev.hoangtuan.shopflow.model.access.Role;
import dev.hoangtuan.shopflow.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "shopflow.account-test-context=true",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "shopflow.auth.otp-hmac-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountSelfServiceTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @MockitoBean Clock clock;
  @MockitoBean ResendEmailClient mail;
  final AtomicReference<Instant> now = new AtomicReference<>();
  final JsonMapper json = new JsonMapper();

  @BeforeEach
  void setup() {
    now.set(Instant.parse("2026-11-07T00:00:00Z"));
    org.mockito.Mockito.when(clock.instant()).thenAnswer(inv -> now.get());
    jdbc.update("DELETE FROM shopflow.auth_email_challenges");
    jdbc.update("DELETE FROM shopflow.auth_rate_buckets");
    jdbc.update("DELETE FROM shopflow.users WHERE email IS NOT NULL");
  }

  @Test
  void profileRequiresAuthenticationAndNeverExposesCredentials() throws Exception {
    mvc.perform(get("/auth/account")).andExpect(status().isUnauthorized());
    long a = account("a@example.com", true, false);
    long b = account("b@example.com", true, false);
    Client client = client(a);
    mvc.perform(get("/auth/account?userId=" + b).session(client.session()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.email").value("a@example.com"))
        .andExpect(jsonPath("$.capabilities.changePassword").value(true))
        .andExpect(jsonPath("$.capabilities.setPassword").value(false))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.authVersion").doesNotExist());
  }

  @Test
  void googleOnlyAndDemoCapabilitiesAreServerDerived() throws Exception {
    Client google = client(account("google@example.com", false, true));
    mvc.perform(get("/auth/account").session(google.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.setPassword").value(true))
        .andExpect(jsonPath("$.capabilities.changePassword").value(false));
    Client demo = client(1);
    mvc.perform(get("/auth/account").session(demo.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.manageDelivery").value(false))
        .andExpect(jsonPath("$.capabilities.changePassword").value(false))
        .andExpect(jsonPath("$.capabilities.setPassword").value(false));
  }

  @Test
  void revokedOrInactiveAccountsCannotReadTheirProfile() throws Exception {
    long id = account("revoked@example.com", true, false);
    Client client = client(id);
    jdbc.update("UPDATE shopflow.users SET auth_version=auth_version+1 WHERE id=?", id);
    mvc.perform(get("/auth/account").session(client.session()))
        .andExpect(status().isUnauthorized());
  }

  long account(String email, boolean local, boolean google) {
    return jdbc.queryForObject(
        """
        INSERT INTO shopflow.users (username,display_name,email,email_verified_at,password_hash,google_issuer,google_sub,role)
        VALUES (?, 'Buyer', ?, now(), ?, ?, ?, 'CUSTOMER') RETURNING id
        """,
        Long.class,
        "test_" + email,
        email,
        local ? encoder.encode("Oldpass1!") : null,
        google ? "https://accounts.google.com" : null,
        google ? "sub-" + email : null);
  }

  Client client(long id) throws Exception {
    String username =
        jdbc.queryForObject("SELECT username FROM shopflow.users WHERE id=?", String.class, id);
    MockHttpSession session = new MockHttpSession();
    var security = SecurityContextHolder.createEmptyContext();
    var principal = new AccountPrincipal(id, username, null, "Buyer", Role.CUSTOMER, 0);
    security.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
    Cookie csrf =
        mvc.perform(get("/auth/session").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return new Client(session, csrf);
  }

  ResultActions call(Client client, HttpMethod method, String path, Object body) throws Exception {
    var request =
        request(method, path)
            .session(client.session())
            .cookie(client.csrf())
            .header("X-XSRF-TOKEN", client.csrf().getValue())
            .contentType(MediaType.APPLICATION_JSON);
    if (body != null) request.content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  record Client(MockHttpSession session, Cookie csrf) {}
}
