package dev.hoangtuan.shopflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.integration.email.ResendEmailClient;
import dev.hoangtuan.shopflow.model.access.Role;
import dev.hoangtuan.shopflow.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
  @Autowired dev.hoangtuan.shopflow.service.CustomerOtpService otpService;
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
    jdbc.update("DELETE FROM shopflow.account_delivery_details");
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

  @Test
  void setupProofIsPrivatePurposeBoundAndRevokesEverySession() throws Exception {
    long id = account("setup@example.com", false, true);
    Client first = client(id);
    Client otherSession = client(id);
    AtomicReference<String> code = new AtomicReference<>();
    doAnswer(
            inv -> {
              code.set(inv.getArgument(2));
              return UUID.randomUUID().toString();
            })
        .when(mail)
        .sendSetupCode(anyString(), anyString(), anyString(), any());
    String body =
        call(first, HttpMethod.POST, "/auth/account/password/setup", Map.of())
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String generation = json.readTree(body).get("challengeId").asText();
    assertThat(code.get()).matches("[0-9]{6}");
    verify(mail)
        .sendSetupCode(
            eq("setup@example.com"), eq("Buyer"), anyString(), eq(UUID.fromString(generation)));
    call(first, HttpMethod.POST, "/auth/otp/resend", Map.of("challengeId", generation))
        .andExpect(status().isBadRequest());
    Map<String, String> proof =
        Map.of("challengeId", generation, "otp", code.get(), "newPassword", "Newpass1!");
    call(otherSession, HttpMethod.POST, "/auth/account/password/setup/confirm", proof)
        .andExpect(status().isBadRequest());
    call(first, HttpMethod.POST, "/auth/password/reset", proof).andExpect(status().isBadRequest());
    call(first, HttpMethod.POST, "/auth/account/password/setup/confirm", proof)
        .andExpect(status().isNoContent());
    assertThat(
            encoder.matches(
                "Newpass1!",
                jdbc.queryForObject(
                    "SELECT password_hash FROM shopflow.users WHERE id=?", String.class, id)))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT auth_version FROM shopflow.users WHERE id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT google_sub FROM shopflow.users WHERE id=?", String.class, id))
        .isEqualTo("sub-setup@example.com");
    mvc.perform(get("/auth/account").session(otherSession.session()))
        .andExpect(status().isUnauthorized());
    assertThat(first.session().isInvalid()).isTrue();
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("username", "setup@example.com", "password", "Newpass1!"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(id))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
  }

  @Test
  void setupRejectsDemoLocalAccountsAndForgedOwnerFields() throws Exception {
    call(client(1), HttpMethod.POST, "/auth/account/password/setup", Map.of())
        .andExpect(status().isForbidden());
    call(
            client(account("local@example.com", true, true)),
            HttpMethod.POST,
            "/auth/account/password/setup",
            Map.of())
        .andExpect(status().isConflict());
    call(
            client(account("owner@example.com", false, true)),
            HttpMethod.POST,
            "/auth/account/password/setup",
            Map.of("email", "other@example.com"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void failedMailCannotBeConfirmedAndResendKeepsTheCooldown() throws Exception {
    Client client = client(account("failed@example.com", false, true));
    doThrow(new dev.hoangtuan.shopflow.exception.access.EmailUnavailableException())
        .when(mail)
        .sendSetupCode(anyString(), anyString(), anyString(), any());
    call(client, HttpMethod.POST, "/auth/account/password/setup", Map.of())
        .andExpect(status().isServiceUnavailable());
    var row =
        jdbc.queryForMap(
            "SELECT challenge_id, status, otp_verifier FROM shopflow.auth_email_challenges WHERE email='failed@example.com'");
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("otp_verifier")).isNull();
    call(
            client,
            HttpMethod.POST,
            "/auth/account/password/setup/resend",
            Map.of("challengeId", row.get("challenge_id")))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void passwordChangeRequiresCurrentProofAndRevokesOtherSessions() throws Exception {
    long id = account("change@example.com", true, true);
    Client first = client(id);
    Client second = client(id);
    call(
            first,
            HttpMethod.POST,
            "/auth/account/password/change",
            Map.of("currentPassword", "wrong", "newPassword", "Newpass1!"))
        .andExpect(status().isBadRequest());
    call(
            first,
            HttpMethod.POST,
            "/auth/account/password/change",
            Map.of("currentPassword", "Oldpass1!", "newPassword", "weak"))
        .andExpect(status().isBadRequest());
    call(
            first,
            HttpMethod.POST,
            "/auth/account/password/change",
            Map.of("currentPassword", "Oldpass1!", "newPassword", "Newpass1!", "userId", 1))
        .andExpect(status().isBadRequest());
    call(
            first,
            HttpMethod.POST,
            "/auth/account/password/change",
            Map.of("currentPassword", "Oldpass1!", "newPassword", "Newpass1!"))
        .andExpect(status().isNoContent());
    assertThat(first.session().isInvalid()).isTrue();
    mvc.perform(get("/auth/account").session(second.session()))
        .andExpect(status().isUnauthorized());
    assertThat(
            encoder.matches(
                "Newpass1!",
                jdbc.queryForObject(
                    "SELECT password_hash FROM shopflow.users WHERE id=?", String.class, id)))
        .isTrue();
    verify(mail).sendPasswordChanged(eq("change@example.com"), eq("Buyer"), any());
  }

  @Test
  void passwordChangeRejectsDemoAndGoogleOnlyAndHonorsCsrfOriginAndAttemptLimits()
      throws Exception {
    var body = Map.of("currentPassword", "Oldpass1!", "newPassword", "Newpass1!");
    call(client(1), HttpMethod.POST, "/auth/account/password/change", body)
        .andExpect(status().isForbidden());
    call(
            client(account("nohash@example.com", false, true)),
            HttpMethod.POST,
            "/auth/account/password/change",
            body)
        .andExpect(status().isForbidden());
    Client own = client(account("limits@example.com", true, false));
    mvc.perform(
            post("/auth/account/password/change")
                .session(own.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/auth/account/password/change")
                .session(own.session())
                .cookie(own.csrf())
                .header("X-XSRF-TOKEN", own.csrf().getValue())
                .header("Origin", "https://evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    for (int i = 0; i < 5; i++)
      call(
              own,
              HttpMethod.POST,
              "/auth/account/password/change",
              Map.of("currentPassword", "wrong", "newPassword", "Newpass1!"))
          .andExpect(status().isBadRequest());
    call(own, HttpMethod.POST, "/auth/account/password/change", body)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void deliveryIsOwnedValidatedAndOneProfilePerAccount() throws Exception {
    long a = account("deliverya@example.com", true, false);
    Client owner = client(a);
    Client other = client(account("deliveryb@example.com", false, true));
    var details =
        Map.of(
            "receiverName",
            " Receiver ",
            "phone",
            "0123456789",
            "addressLine",
            "1 Main street",
            "district",
            "",
            "city",
            "Hanoi");
    call(owner, HttpMethod.GET, "/auth/account/delivery", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.delivery").isEmpty());
    call(owner, HttpMethod.PUT, "/auth/account/delivery", details)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.delivery.receiverName").value("Receiver"));
    call(owner, HttpMethod.PUT, "/auth/account/delivery", details).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shopflow.account_delivery_details WHERE user_id=?",
                Integer.class,
                a))
        .isEqualTo(1);
    call(other, HttpMethod.GET, "/auth/account/delivery?userId=" + a, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.delivery").isEmpty());
    call(other, HttpMethod.DELETE, "/auth/account/delivery?userId=" + a, null)
        .andExpect(status().isNoContent());
    call(owner, HttpMethod.GET, "/auth/account/delivery", null)
        .andExpect(jsonPath("$.delivery.city").value("Hanoi"));
    var forged = new java.util.HashMap<String, Object>(details);
    forged.put("userId", a);
    call(other, HttpMethod.PUT, "/auth/account/delivery", forged)
        .andExpect(status().isBadRequest());
    call(
            owner,
            HttpMethod.PUT,
            "/auth/account/delivery",
            Map.of("receiverName", " ", "phone", "1", "addressLine", "x", "city", "x"))
        .andExpect(status().isBadRequest());
    call(
            owner,
            HttpMethod.PUT,
            "/auth/account/delivery",
            Map.of("receiverName", "x", "phone", "1", "addressLine", "x".repeat(501), "city", "x"))
        .andExpect(status().isBadRequest());
    call(owner, HttpMethod.DELETE, "/auth/account/delivery", null)
        .andExpect(status().isNoContent());
    call(owner, HttpMethod.GET, "/auth/account/delivery", null)
        .andExpect(jsonPath("$.delivery").isEmpty());
    call(client(1), HttpMethod.PUT, "/auth/account/delivery", details)
        .andExpect(status().isForbidden());
    call(client(1), HttpMethod.GET, "/auth/account/delivery", null)
        .andExpect(status().isForbidden());
  }

  @Test
  void setupCodesExpireAfterTenMinutesAndFiveWrongGuessesAreLimited() throws Exception {
    long id = account("expiry@example.com", false, true);
    Client client = client(id);
    AtomicReference<String> code = new AtomicReference<>();
    doAnswer(
            inv -> {
              code.set(inv.getArgument(2));
              return UUID.randomUUID().toString();
            })
        .when(mail)
        .sendSetupCode(anyString(), anyString(), anyString(), any());
    var challenge = otpService.setup(id, 0, null, client.session().getId(), "expiry-ip");
    String wrong = code.get().equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 4; i++)
      call(
              client,
              HttpMethod.POST,
              "/auth/account/password/setup/confirm",
              Map.of(
                  "challengeId", challenge.challengeId(), "otp", wrong, "newPassword", "Newpass1!"))
          .andExpect(status().isBadRequest());
    call(
            client,
            HttpMethod.POST,
            "/auth/account/password/setup/confirm",
            Map.of(
                "challengeId", challenge.challengeId(), "otp", wrong, "newPassword", "Newpass1!"))
        .andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM shopflow.auth_email_challenges WHERE challenge_id=?",
                Integer.class,
                challenge.challengeId()))
        .isEqualTo(5);
    now.set(now.get().plusSeconds(901));
    var fresh = otpService.setup(id, 0, null, client.session().getId(), "expiry-ip");
    now.set(now.get().plusSeconds(600));
    call(
            client,
            HttpMethod.POST,
            "/auth/account/password/setup/confirm",
            Map.of(
                "challengeId", fresh.challengeId(), "otp", code.get(), "newPassword", "Newpass1!"))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM shopflow.users WHERE id=?", String.class, id))
        .isNull();
  }

  @Test
  void twoConcurrentSetupConfirmationsConsumeProofOnlyOnce() throws Exception {
    long id = account("race@example.com", false, true);
    Client client = client(id);
    AtomicReference<String> code = new AtomicReference<>();
    doAnswer(
            inv -> {
              code.set(inv.getArgument(2));
              return UUID.randomUUID().toString();
            })
        .when(mail)
        .sendSetupCode(anyString(), anyString(), anyString(), any());
    var challenge = otpService.setup(id, 0, null, client.session().getId(), "race-ip");
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> attempt =
          () -> {
            start.await();
            try {
              otpService.confirmSetup(
                  id,
                  0,
                  challenge.challengeId(),
                  code.get(),
                  "Newpass1!",
                  client.session().getId(),
                  "race-ip");
              return true;
            } catch (org.springframework.security.core.AuthenticationException
                | dev.hoangtuan.shopflow.exception.access.AccountActionException
                | dev.hoangtuan.shopflow.exception.access.InvalidCodeException e) {
              return false;
            }
          };
      var first = workers.submit(attempt);
      var second = workers.submit(attempt);
      start.countDown();
      assertThat(
              java.util.List.of(
                  first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT auth_version FROM shopflow.users WHERE id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM shopflow.auth_email_challenges WHERE challenge_id=?",
                String.class,
                challenge.challengeId()))
        .isEqualTo("CONSUMED");
  }

  @Test
  void delayedDeliveryResponseCannotResurrectAnOldGeneration() throws Exception {
    long id = account("late@example.com", false, true);
    Client client = client(id);
    var sending = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            inv -> {
              if (calls.incrementAndGet() == 1) {
                sending.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                  throw new AssertionError("Mail callback timeout");
              }
              return UUID.randomUUID().toString();
            })
        .when(mail)
        .sendSetupCode(anyString(), anyString(), anyString(), any());
    try (var workers = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var old =
          workers.submit(() -> otpService.setup(id, 0, null, client.session().getId(), "late-ip"));
      assertThat(sending.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      now.set(now.get().plusSeconds(61));
      var fresh = otpService.setup(id, 0, null, client.session().getId(), "late-ip");
      release.countDown();
      assertThat(old.get(10, java.util.concurrent.TimeUnit.SECONDS).challengeId())
          .isNotEqualTo(fresh.challengeId());
      assertThat(
              jdbc.queryForObject(
                  "SELECT challenge_id FROM shopflow.auth_email_challenges WHERE email='late@example.com'",
                  UUID.class))
          .isEqualTo(fresh.challengeId());
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM shopflow.auth_email_challenges WHERE email='late@example.com'",
                  String.class))
          .isEqualTo("ISSUED");
    } finally {
      release.countDown();
    }
  }

  @Test
  void setupAndResendUseTheAccountsChosenDisplayName() throws Exception {
    long id = account("named@example.com", false, true);
    jdbc.update("UPDATE shopflow.users SET display_name='hoangtuan2k5' WHERE id=?", id);
    Client current = client(id);
    String response =
        call(current, HttpMethod.POST, "/auth/account/password/setup", Map.of())
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var generation = UUID.fromString(json.readTree(response).get("challengeId").asText());
    now.set(now.get().plusSeconds(61));
    call(
            current,
            HttpMethod.POST,
            "/auth/account/password/setup/resend",
            Map.of("challengeId", generation))
        .andExpect(status().isAccepted());
    verify(mail, times(2))
        .sendSetupCode(eq("named@example.com"), eq("hoangtuan2k5"), anyString(), any(UUID.class));
    assertThat(
            jdbc.queryForObject(
                "SELECT pending_display_name FROM shopflow.auth_email_challenges WHERE email='named@example.com'",
                String.class))
        .isNull();
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
