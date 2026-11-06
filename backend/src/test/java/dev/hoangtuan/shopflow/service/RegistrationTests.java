package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import dev.hoangtuan.shopflow.integration.email.ResendEmailClient;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "shopflow.auth.otp-hmac-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistrationTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @MockitoBean ResendEmailClient mail;
  @MockitoBean Clock clock;
  final AtomicReference<Instant> now = new AtomicReference<>();
  final Map<UUID, String> codes = new ConcurrentHashMap<>();
  final JsonMapper json = new JsonMapper();

  @BeforeEach
  void setup() {
    now.set(Instant.parse("2026-11-05T00:00:00Z"));
    when(clock.instant()).thenAnswer(inv -> now.get());
    jdbc.update("DELETE FROM shopflow.auth_email_challenges");
    jdbc.update("DELETE FROM shopflow.auth_rate_buckets");
    jdbc.update("DELETE FROM shopflow.users WHERE email IS NOT NULL");
    codes.clear();
    when(mail.sendRegistrationCode(anyString(), anyString(), anyString(), any(UUID.class)))
        .thenAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              UUID id = inv.getArgument(3);
              assertThat(
                      jdbc.queryForObject(
                          "SELECT status FROM shopflow.auth_email_challenges WHERE challenge_id = ?",
                          String.class,
                          id))
                  .isEqualTo("PENDING_SEND");
              codes.put(id, inv.getArgument(2));
              return UUID.randomUUID().toString();
            });
  }

  @Test
  void websiteLanguageReachesEmailDelivery() throws Exception {
    when(mail.sendRegistrationCode(anyString(), anyString(), anyString(), any(UUID.class)))
        .thenAnswer(
            inv -> {
              assertThat(
                      org.springframework.context.i18n.LocaleContextHolder.getLocale()
                          .getLanguage())
                  .isEqualTo("vi");
              return UUID.randomUUID().toString();
            });
    mvc.perform(
            postJson(
                    "/auth/registrations",
                    Map.of(
                        "email",
                        "language@example.com",
                        "password",
                        "Abcdef1!",
                        "displayName",
                        "Khách"))
                .header("Accept-Language", "vi")
                .with(csrf()))
        .andExpect(status().isAccepted());
    verify(mail)
        .sendRegistrationCode(
            eq("language@example.com"), eq("Khách"), anyString(), any(UUID.class));
  }

  @Test
  void activatesOnlyAfterProofThenLogsInByEmailWithoutTechnicalAlias() throws Exception {
    MvcResult issued = register("  Buyer@EXAMPLE.com  ", "Abcdef1!", "  Khách  ");
    UUID id = id(issued);
    MockHttpSession session = session(issued);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email IS NOT NULL", Integer.class))
        .isZero();
    assertThat(issued.getResponse().getContentAsString())
        .doesNotContain("password", "otp", "buyer@example.com", "verifier");
    String hash =
        jdbc.queryForObject(
            "SELECT pending_password_hash FROM shopflow.auth_email_challenges", String.class);
    assertThat(encoder.matches("Abcdef1!", hash)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT octet_length(session_binding) FROM shopflow.auth_email_challenges",
                Integer.class))
        .isEqualTo(32);
    mvc.perform(
            postJson(
                "/auth/login", Map.of("username", "buyer@example.com", "password", "Abcdef1!")))
        .andExpect(status().isUnauthorized());
    verifyCode(id, codes.get(id), session).andExpect(status().isNoContent());
    mvc.perform(get("/auth/session").session(session))
        .andExpect(jsonPath("$.authenticated").value(false));
    var user = jdbc.queryForMap("SELECT * FROM shopflow.users WHERE email = 'buyer@example.com'");
    assertThat(user).containsEntry("role", "CUSTOMER").containsEntry("display_name", "Khách");
    mvc.perform(
            postJson(
                "/auth/login", Map.of("username", " BUYER@EXAMPLE.COM ", "password", "Abcdef1!")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
    mvc.perform(
            postJson(
                "/auth/login", Map.of("username", user.get("username"), "password", "Abcdef1!")))
        .andExpect(status().isUnauthorized());
    verifyCode(id, codes.get(id), session).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForMap(
                    "SELECT otp_verifier, pending_password_hash, pending_display_name FROM shopflow.auth_email_challenges")
                .values())
        .containsOnlyNulls();
  }

  @Test
  void persistsFiveFailuresAndDoesNotResetEmailBudgetOnResend() throws Exception {
    MvcResult first = register("retry@example.com", "Abcdef1!", "Retry");
    MockHttpSession session = session(first);
    UUID old = id(first);
    mvc.perform(
            postJson("/auth/otp/resend", Map.of("challengeId", old)).session(session).with(csrf()))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
    now.set(now.get().plusSeconds(60));
    MvcResult resent =
        mvc.perform(
                postJson("/auth/otp/resend", Map.of("challengeId", old))
                    .session(session)
                    .with(csrf()))
            .andExpect(status().isAccepted())
            .andReturn();
    UUID current = id(resent);
    verifyCode(old, codes.get(old), session).andExpect(status().isBadRequest());
    String wrong = codes.get(current).equals("999999") ? "888888" : "999999";
    for (int i = 0; i < 4; i++)
      verifyCode(current, wrong, session).andExpect(status().isBadRequest());
    verifyCode(current, wrong, session).andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM shopflow.auth_email_challenges", Integer.class))
        .isEqualTo(5);
    now.set(now.get().plusSeconds(60));
    MvcResult third =
        mvc.perform(
                postJson("/auth/otp/resend", Map.of("challengeId", current))
                    .session(session)
                    .with(csrf()))
            .andExpect(status().isAccepted())
            .andReturn();
    verifyCode(id(third), codes.get(id(third)), session).andExpect(status().isTooManyRequests());
    now.set(now.get().plusSeconds(60));
    mvc.perform(
            postJson("/auth/otp/resend", Map.of("challengeId", id(third)))
                .session(session)
                .with(csrf()))
        .andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT used_count FROM shopflow.auth_rate_buckets WHERE scope = 'MAIL_DAILY'",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  void treatsExistingAccountAsSyntheticWithSameFloorAndNeverChangesIt() throws Exception {
    jdbc.update(
        "INSERT INTO shopflow.users (username,email,email_verified_at,password_hash,display_name,role) VALUES ('existing', 'known@example.com', CURRENT_TIMESTAMP, 'original-hash', 'Existing', 'SHOP_OWNER')");
    long started = System.nanoTime();
    MvcResult result = register("KNOWN@example.com", "Abcdef1!", "Attacker");
    assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
        .isGreaterThanOrEqualTo(java.time.Duration.ofMillis(3900));
    verify(mail, never()).sendRegistrationCode(anyString(), anyString(), anyString(), any());
    assertThat(
            jdbc.queryForMap(
                "SELECT password_hash, role FROM shopflow.users WHERE email = 'known@example.com'"))
        .containsEntry("password_hash", "original-hash")
        .containsEntry("role", "SHOP_OWNER");
    assertThat(
            jdbc.queryForObject(
                "SELECT synthetic FROM shopflow.auth_email_challenges", Boolean.class))
        .isTrue();
    verifyCode(id(result), "012345", session(result)).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT used_count FROM shopflow.auth_rate_buckets WHERE scope = 'MAIL_DAILY'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void acceptsProviderFailureWithoutIssuingUsableCode() throws Exception {
    when(mail.sendRegistrationCode(anyString(), anyString(), anyString(), any()))
        .thenThrow(new EmailUnavailableException());
    MvcResult result = register("failed@example.com", "Abcdef1!", "Failed");
    assertThat(jdbc.queryForMap("SELECT status, otp_verifier FROM shopflow.auth_email_challenges"))
        .containsEntry("status", "FAILED")
        .containsEntry("otp_verifier", null);
    for (int attempt = 0; attempt < 4; attempt++)
      verifyCode(id(result), "012345", session(result)).andExpect(status().isBadRequest());
    verifyCode(id(result), "012345", session(result)).andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email = 'failed@example.com'",
                Integer.class))
        .isZero();
  }

  @Test
  void rejectsWrongSessionAndExpiresExactlyAtTenMinutes() throws Exception {
    MvcResult first = register("boundary@example.com", "Abcdef1!", "Boundary");
    verifyCode(id(first), codes.get(id(first)), new MockHttpSession())
        .andExpect(status().isBadRequest());
    now.set(now.get().plusSeconds(600));
    verifyCode(id(first), codes.get(id(first)), session(first)).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject("SELECT status FROM shopflow.auth_email_challenges", String.class))
        .isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "SELECT pending_password_hash FROM shopflow.auth_email_challenges", String.class))
        .isNull();
    MvcResult second = register("before@example.com", "Abcdef1!", "Before");
    now.set(now.get().plusSeconds(599).plusMillis(999));
    verifyCode(id(second), codes.get(id(second)), session(second))
        .andExpect(status().isNoContent());
  }

  @Test
  void serializesConcurrentVerificationAndNeverAdoptsGoogleCollision() throws Exception {
    MvcResult first = register("race@example.com", "Abcdef1!", "Race");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () ->
                  verifyCode(id(first), codes.get(id(first)), session(first))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var two =
          executor.submit(
              () ->
                  verifyCode(id(first), codes.get(id(first)), session(first))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(204, 400);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email = 'race@example.com'",
                Integer.class))
        .isEqualTo(1);
    MvcResult second = register("google-race@example.com", "Abcdef1!", "Pending");
    jdbc.update(
        "INSERT INTO shopflow.users (username,email,email_verified_at,google_issuer,google_sub,display_name,role) VALUES ('google-collision','google-race@example.com',CURRENT_TIMESTAMP,'https://accounts.google.com','subject','Google','CUSTOMER')");
    verifyCode(id(second), codes.get(id(second)), session(second))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM shopflow.users WHERE email = 'google-race@example.com'",
                String.class))
        .isNull();
  }

  @Test
  void rejectsUnknownFieldsAndCsrfOrOriginBeforeSideEffects() throws Exception {
    String body =
        "{\"email\":\"buyer@example.com\",\"password\":\"Abcdef1!\",\"displayName\":\"Buyer\",\"role\":\"SHOP_OWNER\"}";
    mvc.perform(
            post("/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(postJson("/auth/registrations", Map.of("email", "buyer@example.com")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Forbidden request"));
    mvc.perform(
            postJson("/auth/registrations", Map.of("email", "buyer@example.com"))
                .with(csrf())
                .header("Origin", "https://evil.invalid"))
        .andExpect(status().isForbidden());
    mvc.perform(
            postJson(
                    "/auth/registrations",
                    Map.of("email", "invalid", "password", "Abcdef1!", "displayName", "Buyer"))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_email_challenges", Integer.class))
        .isZero();
    verify(mail, never()).sendRegistrationCode(anyString(), anyString(), anyString(), any());
  }

  @Test
  void countsRandomVerifyIdsByPeerIpAndIgnoresSpoofedForwardingHeaders() throws Exception {
    for (int i = 0; i < 30; i++) {
      mvc.perform(
              postJson(
                      "/auth/registrations/verify",
                      Map.of("challengeId", UUID.randomUUID(), "otp", "012345"))
                  .with(csrf())
                  .header("X-Forwarded-For", "192.0.2." + i))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            postJson(
                    "/auth/registrations/verify",
                    Map.of("challengeId", UUID.randomUUID(), "otp", "012345"))
                .with(csrf()))
        .andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM shopflow.auth_rate_buckets", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_email_challenges", Integer.class))
        .isZero();
  }

  @Test
  void concurrentRegistrationsKeepOnlyOneGenerationAndCredential() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      executor.submit(
                          () ->
                              mvc.perform(
                                      postJson(
                                              "/auth/registrations",
                                              Map.of(
                                                  "email",
                                                  i == 0 ? "RACE@example.com" : "race@example.com",
                                                  "password",
                                                  i == 0 ? "Abcdef1!" : "Different2!",
                                                  "displayName",
                                                  "Race"))
                                          .with(csrf()))
                                  .andReturn()))
              .toList();
      var first = tasks.get(0).get();
      var second = tasks.get(1).get();
      assertThat(
              java.util.List.of(first.getResponse().getStatus(), second.getResponse().getStatus()))
          .containsExactlyInAnyOrder(202, 429);
      MvcResult winner = first.getResponse().getStatus() == 202 ? first : second;
      verifyCode(id(winner), codes.get(id(winner)), session(winner))
          .andExpect(status().isNoContent());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email = 'race@example.com'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT used_count FROM shopflow.auth_rate_buckets WHERE scope = 'MAIL_DAILY'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void globalBudgetRejectionDoesNotPartiallyReserveOtherBuckets() throws Exception {
    register("first@example.com", "Abcdef1!", "First");
    jdbc.update("UPDATE shopflow.auth_rate_buckets SET used_count = 90 WHERE scope = 'MAIL_DAILY'");
    mvc.perform(
            postJson(
                    "/auth/registrations",
                    Map.of(
                        "email",
                        "second@example.com",
                        "password",
                        "Abcdef1!",
                        "displayName",
                        "Second"))
                .with(csrf()))
        .andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT used_count FROM shopflow.auth_rate_buckets WHERE scope = 'IP_SEND'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_rate_buckets WHERE scope = 'EMAIL_SEND'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_email_challenges", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void lateDeliveryCannotReactivateSupersededGeneration() throws Exception {
    var sending = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    when(mail.sendRegistrationCode(anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            inv -> {
              codes.put(inv.getArgument(3), inv.getArgument(2));
              if (calls.incrementAndGet() == 1) {
                sending.countDown();
                assertThat(release.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              }
              return UUID.randomUUID().toString();
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> register("late@example.com", "Abcdef1!", "Old"));
      assertThat(sending.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      now.set(now.get().plusSeconds(60));
      MvcResult replacement;
      try {
        replacement = register("late@example.com", "Newpass2!", "New");
      } finally {
        release.countDown();
      }
      MvcResult old = pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
      verifyCode(id(old), codes.get(id(old)), session(old)).andExpect(status().isBadRequest());
      verifyCode(id(replacement), codes.get(id(replacement)), session(replacement))
          .andExpect(status().isNoContent());
    }
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM shopflow.users WHERE email = 'late@example.com'",
            String.class);
    assertThat(encoder.matches("Newpass2!", hash)).isTrue();
    assertThat(encoder.matches("Abcdef1!", hash)).isFalse();
  }

  @Test
  void missingMailConfigurationReturnsUniform503WithoutChallenges() throws Exception {
    doThrow(new EmailUnavailableException()).when(mail).requireConfigured();
    for (String email : java.util.List.of("unknown@example.com", "other@example.com")) {
      mvc.perform(
              postJson(
                      "/auth/registrations",
                      Map.of("email", email, "password", "Abcdef1!", "displayName", "Buyer"))
                  .with(csrf()))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.message").value("Authentication service unavailable"));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_email_challenges", Integer.class))
        .isZero();
  }

  private MvcResult register(String email, String password, String name) throws Exception {
    return mvc.perform(
            postJson(
                    "/auth/registrations",
                    Map.of("email", email, "password", password, "displayName", name))
                .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.expiresInSeconds").value(600))
        .andReturn();
  }

  private org.springframework.test.web.servlet.ResultActions verifyCode(
      UUID id, String code, MockHttpSession session) throws Exception {
    return mvc.perform(
        postJson("/auth/registrations/verify", Map.of("challengeId", id, "otp", code))
            .session(session)
            .with(csrf()));
  }

  private UUID id(MvcResult result) throws Exception {
    return UUID.fromString(
        json.readTree(result.getResponse().getContentAsString()).get("challengeId").asText());
  }

  private MockHttpSession session(MvcResult result) {
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
  }
}
