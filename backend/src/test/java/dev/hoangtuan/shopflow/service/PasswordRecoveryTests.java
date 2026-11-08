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
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
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
import org.springframework.test.web.servlet.ResultActions;
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
class PasswordRecoveryTests {
  private static final String LOCAL = "recovery@example.com";
  private static final String OLD = "Oldpass1!";
  private static final String NEW = "Newpass2!";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired LoginRateLimiter loginLimits;
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
    loginLimits.clear(LOCAL);
    codes.clear();
    jdbc.update(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,password_hash,display_name,role)
        VALUES ('recovery-local', ?, CURRENT_TIMESTAMP, ?, 'Recovery', 'CUSTOMER')
        """,
        LOCAL,
        encoder.encode(OLD));
    when(mail.sendResetCode(anyString(), anyString(), anyString(), any(UUID.class)))
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
    when(mail.sendRegistrationCode(anyString(), anyString(), anyString(), any(UUID.class)))
        .thenAnswer(
            inv -> {
              codes.put(inv.getArgument(3), inv.getArgument(2));
              return UUID.randomUUID().toString();
            });
    when(mail.sendPasswordChanged(anyString(), anyString(), any(UUID.class)))
        .thenAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(
                      jdbc.queryForObject(
                          "SELECT auth_version FROM shopflow.users WHERE email = ?",
                          Long.class,
                          inv.<String>getArgument(0)))
                  .isEqualTo(1L);
              return UUID.randomUUID().toString();
            });
  }

  @Test
  void changesPasswordAtomicallyAndRevokesTwoOldSessionsWithoutAutoLogin() throws Exception {
    MockHttpSession first = session(login(LOCAL, OLD).andExpect(status().isOk()).andReturn());
    MockHttpSession second = session(login(LOCAL, OLD).andExpect(status().isOk()).andReturn());
    MvcResult issued = forgot(" RECOVERY@EXAMPLE.COM ");
    verify(mail).sendResetCode(eq(LOCAL), eq("Recovery"), anyString(), eq(id(issued)));
    mvc.perform(get("/auth/session").session(first))
        .andExpect(jsonPath("$.authenticated").value(true));
    mvc.perform(get("/auth/session").session(second))
        .andExpect(jsonPath("$.authenticated").value(true));
    assertThat(version()).isZero();
    UUID outstanding = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO shopflow.auth_email_challenges (email,purpose,challenge_id,user_id,
          session_binding,otp_verifier,synthetic,status,issued_at,expires_at,resend_at)
        SELECT 'old-address@example.com','RESET_PASSWORD',?,id,?,?,FALSE,'ISSUED',
          CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '10' MINUTE,
          CURRENT_TIMESTAMP + INTERVAL '60' SECOND FROM shopflow.users WHERE email = ?
        """,
        outstanding,
        new byte[32],
        new byte[32],
        LOCAL);
    reset(id(issued), codes.get(id(issued)), NEW, session(issued))
        .andExpect(status().isNoContent());
    assertThat(version()).isEqualTo(1L);
    assertThat(encoder.matches(NEW, hash())).isTrue();
    mvc.perform(get("/auth/session").session(first))
        .andExpect(jsonPath("$.authenticated").value(false));
    mvc.perform(get("/inventory").session(second)).andExpect(status().isUnauthorized());
    mvc.perform(get("/auth/session").session(session(issued)))
        .andExpect(jsonPath("$.authenticated").value(false));
    login(LOCAL, OLD).andExpect(status().isUnauthorized());
    login(LOCAL.toUpperCase(), NEW).andExpect(status().isOk());
    reset(id(issued), codes.get(id(issued)), "Otherpass3!", session(issued))
        .andExpect(status().isBadRequest());
    verify(mail, times(1)).sendPasswordChanged(eq(LOCAL), eq("Recovery"), eq(id(issued)));
    assertThat(
            jdbc.queryForObject(
                "SELECT otp_verifier FROM shopflow.auth_email_challenges WHERE challenge_id = ?",
                byte[].class,
                id(issued)))
        .isNull();
    assertThat(
            jdbc.queryForMap(
                "SELECT status,otp_verifier FROM shopflow.auth_email_challenges WHERE challenge_id = ?",
                outstanding))
        .containsEntry("status", "EXPIRED")
        .containsEntry("otp_verifier", null);
  }

  @Test
  void unknownInactiveAndGoogleOnlyGetSyntheticResponseWithoutMailOrCredentialChanges()
      throws Exception {
    jdbc.update(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,password_hash,display_name,role,active)
        VALUES ('inactive','inactive@example.com',CURRENT_TIMESTAMP,'original-hash','Inactive','CUSTOMER',FALSE)
        """);
    jdbc.update(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,google_issuer,google_sub,display_name,role)
        VALUES ('google','google@example.com',CURRENT_TIMESTAMP,'https://accounts.google.com','subject','Google','CUSTOMER')
        """);
    for (String email :
        new String[] {"unknown@example.com", "inactive@example.com", "google@example.com"}) {
      long started = System.nanoTime();
      MvcResult issued = forgot(email);
      assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
          .isGreaterThanOrEqualTo(java.time.Duration.ofMillis(3900));
      reset(id(issued), "012345", NEW, session(issued)).andExpect(status().isBadRequest());
    }
    verify(mail, never()).sendResetCode(anyString(), anyString(), anyString(), any());
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM shopflow.users WHERE email = 'google@example.com'",
                String.class))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM shopflow.users WHERE email = 'inactive@example.com'",
                Boolean.class))
        .isFalse();
    assertThat(version()).isZero();
  }

  @Test
  void honorsExactExpiryAndRejectsSessionAndPurposeMismatches() throws Exception {
    MvcResult first = forgot(LOCAL);
    reset(id(first), codes.get(id(first)), NEW, new MockHttpSession())
        .andExpect(status().isBadRequest());
    now.set(now.get().plusSeconds(599));
    reset(id(first), codes.get(id(first)), NEW, session(first)).andExpect(status().isNoContent());
    MvcResult second = forgot(LOCAL);
    now.set(now.get().plusSeconds(600));
    reset(id(second), codes.get(id(second)), "Otherpass3!", session(second))
        .andExpect(status().isBadRequest());
    assertThat(encoder.matches(NEW, hash())).isTrue();
    assertThat(version()).isEqualTo(1L);
    MvcResult registration =
        mvc.perform(
                postJson(
                        "/auth/registrations",
                        Map.of(
                            "email",
                            "signup@example.com",
                            "password",
                            OLD,
                            "displayName",
                            "Signup"))
                    .with(csrf()))
            .andExpect(status().isAccepted())
            .andReturn();
    reset(id(registration), codes.get(id(registration)), NEW, session(registration))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resendReplacesGenerationButRetainsFailureBudget() throws Exception {
    MvcResult issued = forgot(LOCAL);
    UUID old = id(issued);
    String wrong = codes.get(old).equals("999999") ? "888888" : "999999";
    for (int i = 0; i < 4; i++)
      reset(old, wrong, NEW, session(issued)).andExpect(status().isBadRequest());
    mvc.perform(
            postJson("/auth/otp/resend", Map.of("challengeId", old))
                .session(session(issued))
                .with(csrf()))
        .andExpect(status().isTooManyRequests());
    now.set(now.get().plusSeconds(60));
    MvcResult resent =
        mvc.perform(
                postJson("/auth/otp/resend", Map.of("challengeId", old))
                    .session(session(issued))
                    .with(csrf()))
            .andExpect(status().isAccepted())
            .andReturn();
    reset(old, codes.get(old), NEW, session(issued)).andExpect(status().isBadRequest());
    UUID current = id(resent);
    wrong = codes.get(current).equals("999999") ? "888888" : "999999";
    reset(current, wrong, NEW, session(issued)).andExpect(status().isTooManyRequests());
    reset(current, codes.get(current), NEW, session(issued))
        .andExpect(status().isTooManyRequests());
    assertThat(version()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT used_count FROM shopflow.auth_rate_buckets WHERE scope = 'EMAIL_VERIFY_FAILURE'",
                Integer.class))
        .isEqualTo(5);
  }

  @Test
  void twoConcurrentResetsConsumeOnlyOnce() throws Exception {
    MvcResult issued = forgot(LOCAL);
    UUID id = id(issued);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  reset(id, codes.get(id), NEW, session(issued))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var second =
          executor.submit(
              () ->
                  reset(id, codes.get(id), "Otherpass3!", session(issued))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      int a = first.get(15, java.util.concurrent.TimeUnit.SECONDS),
          b = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(java.util.List.of(a, b)).containsExactlyInAnyOrder(204, 400);
      assertThat(encoder.matches(a == 204 ? NEW : "Otherpass3!", hash())).isTrue();
    }
    assertThat(version()).isEqualTo(1L);
    verify(mail, times(1)).sendPasswordChanged(eq(LOCAL), eq("Recovery"), eq(id));
  }

  @Test
  void deliveryOrNotificationFailureCannotCreateUsableProofOrUndoReset() throws Exception {
    when(mail.sendResetCode(anyString(), anyString(), anyString(), any()))
        .thenThrow(new EmailUnavailableException());
    MvcResult failed = forgot(LOCAL);
    for (int attempt = 0; attempt < 4; attempt++)
      reset(id(failed), "012345", NEW, session(failed)).andExpect(status().isBadRequest());
    reset(id(failed), "012345", NEW, session(failed)).andExpect(status().isTooManyRequests());
    assertThat(version()).isZero();
    jdbc.update("DELETE FROM shopflow.auth_email_challenges");
    jdbc.update("DELETE FROM shopflow.auth_rate_buckets");
    doAnswer(
            inv -> {
              codes.put(inv.getArgument(3), inv.getArgument(2));
              return UUID.randomUUID().toString();
            })
        .when(mail)
        .sendResetCode(anyString(), anyString(), anyString(), any());
    doThrow(new EmailUnavailableException())
        .when(mail)
        .sendPasswordChanged(anyString(), anyString(), any());
    MvcResult issued = forgot(LOCAL);
    reset(id(issued), codes.get(id(issued)), NEW, session(issued))
        .andExpect(status().isNoContent());
    assertThat(encoder.matches(NEW, hash())).isTrue();
    assertThat(version()).isEqualTo(1L);
  }

  @Test
  void requestDoesNotClearLoginFailuresButSuccessfulResetDoes() throws Exception {
    for (int i = 0; i < 5; i++) login(LOCAL, "Wrongpass1!").andExpect(status().isUnauthorized());
    MvcResult issued = forgot(LOCAL);
    login(LOCAL, OLD).andExpect(status().isTooManyRequests());
    reset(id(issued), codes.get(id(issued)), NEW, session(issued))
        .andExpect(status().isNoContent());
    login(LOCAL, NEW).andExpect(status().isOk());
  }

  @Test
  void invalidPasswordOrChangedAccountNeverConsumesProof() throws Exception {
    MvcResult issued = forgot(LOCAL);
    for (String password :
        new String[] {
          "Abcde1!", "abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdef12", "Aa1!" + "x".repeat(69)
        })
      reset(id(issued), codes.get(id(issued)), password, session(issued))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    assertThat(
            jdbc.queryForObject("SELECT status FROM shopflow.auth_email_challenges", String.class))
        .isEqualTo("ISSUED");
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE email = ?", LOCAL);
    reset(id(issued), codes.get(id(issued)), NEW, session(issued))
        .andExpect(status().isBadRequest());
    assertThat(version()).isZero();
  }

  @Test
  void csrfOriginUnknownFieldsAndRandomIdsCannotBypassControls() throws Exception {
    mvc.perform(postJson("/auth/password/forgot", Map.of("email", LOCAL)))
        .andExpect(status().isForbidden());
    mvc.perform(
            postJson("/auth/password/forgot", Map.of("email", LOCAL))
                .with(csrf())
                .header("Origin", "https://evil.invalid"))
        .andExpect(status().isForbidden());
    mvc.perform(
            postJson("/auth/password/forgot", Map.of("email", LOCAL, "role", "SHOP_OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.auth_email_challenges", Integer.class))
        .isZero();
    for (int i = 0; i < 30; i++)
      reset(UUID.randomUUID(), "012345", NEW, new MockHttpSession())
          .andExpect(status().isBadRequest());
    reset(UUID.randomUUID(), "012345", NEW, new MockHttpSession())
        .andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM shopflow.auth_rate_buckets", Integer.class))
        .isEqualTo(1);
  }

  private MvcResult forgot(String email) throws Exception {
    return mvc.perform(postJson("/auth/password/forgot", Map.of("email", email)).with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.expiresInSeconds").value(600))
        .andReturn();
  }

  private ResultActions reset(UUID id, String otp, String password, MockHttpSession session)
      throws Exception {
    return mvc.perform(
        postJson(
                "/auth/password/reset",
                Map.of("challengeId", id, "otp", otp, "newPassword", password))
            .session(session)
            .with(csrf()));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(postJson("/auth/login", Map.of("username", email, "password", password)));
  }

  private String hash() {
    return jdbc.queryForObject(
        "SELECT password_hash FROM shopflow.users WHERE email = ?", String.class, LOCAL);
  }

  private long version() {
    return jdbc.queryForObject(
        "SELECT auth_version FROM shopflow.users WHERE email = ?", Long.class, LOCAL);
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
