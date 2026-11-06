package dev.hoangtuan.shopflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import dev.hoangtuan.shopflow.security.LoginRateLimiter;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "shopflow.auth.google.client-id=test-client",
      "shopflow.auth.google.client-secret=test-secret"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoogleLoginTests {
  private static final String HOST = "shopflow.hoangtuan.dev";
  private static final String EMAIL = "google-test@example.com";
  private static final String PASSWORD = "Existing1!";
  private static final String CALLBACK = "/login/oauth2/code/google";
  private static final JsonMapper JSON = new JsonMapper();
  private static final Map<String, Token> CODES = new ConcurrentHashMap<>();
  private static final Map<String, Map<String, Object>> ACCESS = new ConcurrentHashMap<>();
  private static HttpServer provider;
  private static RSAKey key;
  private static RSAKey wrongKey;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired LoginRateLimiter limits;
  @MockitoBean ClientRegistrationRepository clients;
  @MockitoBean Clock clock;
  private final AtomicReference<Instant> now = new AtomicReference<>();

  @BeforeAll
  static void provider() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    wrongKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/jwks", ex -> reply(ex, 200, new JWKSet(key.toPublicJWK()).toJSONObject()));
    provider.createContext(
        "/userinfo",
        ex ->
            reply(
                ex,
                200,
                ACCESS.get(ex.getRequestHeaders().getFirst("Authorization").substring(7))));
    provider.createContext(
        "/token",
        ex -> {
          try {
            Map<String, String> params =
                query(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Token token = CODES.remove(params.get("code"));
            if (token == null) {
              reply(ex, 400, Map.of("error", "invalid_grant"));
              return;
            }
            String challenge =
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                        MessageDigest.getInstance("SHA-256")
                            .digest(
                                params.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
            if (!challenge.equals(token.flow().challenge())
                || !params.get("redirect_uri").equals(token.flow().callback())) {
              reply(ex, 400, Map.of("error", "invalid_grant"));
              return;
            }
            Map<String, Object> claims = new HashMap<>(token.claims());
            claims.putIfAbsent("iss", "https://accounts.google.com");
            claims.putIfAbsent("aud", "test-client");
            claims.putIfAbsent("sub", "subject-one");
            claims.putIfAbsent("iat", Date.from(Instant.now().minusSeconds(10)));
            claims.putIfAbsent("exp", Date.from(Instant.now().plusSeconds(300)));
            claims.putIfAbsent("nonce", token.flow().nonce());
            claims.putIfAbsent("email", EMAIL);
            claims.putIfAbsent("email_verified", true);
            claims.putIfAbsent("name", "Google Customer");
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
            claims.forEach(builder::claim);
            SignedJWT jwt =
                new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("test-key")
                        .type(JOSEObjectType.JWT)
                        .build(),
                    builder.build());
            jwt.sign(new RSASSASigner(token.wrongSignature() ? wrongKey : key));
            String access = UUID.randomUUID().toString();
            ACCESS.put(
                access,
                Map.of(
                    "sub",
                    claims.get("sub"),
                    "email",
                    claims.get("email"),
                    "email_verified",
                    claims.get("email_verified"),
                    "name",
                    claims.get("name")));
            reply(
                ex,
                200,
                Map.of(
                    "access_token",
                    access,
                    "token_type",
                    "Bearer",
                    "expires_in",
                    300,
                    "scope",
                    "openid email profile",
                    "id_token",
                    jwt.serialize()));
          } catch (Exception failure) {
            reply(ex, 400, Map.of("error", "invalid_grant"));
          }
        });
    provider.start();
  }

  @AfterAll
  static void stop() {
    provider.stop(0);
  }

  @BeforeEach
  void setup() {
    now.set(Instant.now());
    when(clock.instant()).thenAnswer(inv -> now.get());
    String base = "http://127.0.0.1:" + provider.getAddress().getPort();
    when(clients.findByRegistrationId("google"))
        .thenReturn(
            ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://" + HOST + "/api" + CALLBACK)
                .scope("openid", "email", "profile")
                .authorizationUri(base + "/authorize")
                .tokenUri(base + "/token")
                .jwkSetUri(base + "/jwks")
                .userInfoUri(base + "/userinfo")
                .userNameAttributeName("sub")
                .issuerUri("https://accounts.google.com")
                .build());
    jdbc.update("DELETE FROM shopflow.auth_email_challenges");
    jdbc.update("DELETE FROM shopflow.users WHERE email IS NOT NULL");
    limits.clear(EMAIL);
    CODES.clear();
    ACCESS.clear();
  }

  @Test
  void newReturningAndChangedEmailUseTheSameInternalPrincipal() throws Exception {
    Flow flow = start(HOST);
    MvcResult result =
        callback(flow, Map.of(), false)
            .andExpect(status().isSeeOther())
            .andExpect(header().string("Location", "/customer?checkout=1"))
            .andReturn();
    MockHttpSession session = session(result);
    mvc.perform(get("/auth/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    Long id =
        jdbc.queryForObject("SELECT id FROM shopflow.users WHERE email = ?", Long.class, EMAIL);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM shopflow.users WHERE id = ?", String.class, id))
        .isNull();
    assertThat(session.getId()).isNotEqualTo(flow.session().getId());
    callback(start(HOST), Map.of("email", "changed@example.com"), false)
        .andExpect(status().isSeeOther());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email IS NOT NULL", Integer.class))
        .isEqualTo(1);
    mvc.perform(get("/auth/session").session(session))
        .andExpect(jsonPath("$.user.userId").value(id));
    for (String name : java.util.Collections.list(session.getAttributeNames())) {
      assertThat(name).doesNotContain("AUTHORIZED_CLIENT", "AUTHORIZATION_REQUEST");
      assertThat(session.getAttribute(name).toString()).doesNotContain("access_token", "id_token");
    }
    var boot = mvc.perform(get("/auth/session").session(session)).andReturn().getResponse();
    Cookie csrfCookie = boot.getCookie("XSRF-TOKEN");
    assertThat(csrfCookie).isNotNull();
    mvc.perform(
            post("/auth/logout")
                .session(session)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void rejectsInvalidTokensAndStateThroughTheRealOidcDecoder() throws Exception {
    for (Map<String, Object> invalid :
        java.util.List.<Map<String, Object>>of(
            Map.of("iss", "https://evil.example"), Map.of("aud", "another-client"),
            Map.of("nonce", "wrong-nonce"),
                Map.of("exp", Date.from(Instant.now().minusSeconds(600))),
            Map.of("email_verified", false), Map.of("email", "invalid email"))) {
      callback(start(HOST), invalid, false)
          .andExpect(status().isSeeOther())
          .andExpect(header().string("Location", "/login?google=failed"));
    }
    callback(start(HOST), Map.of(), true)
        .andExpect(header().string("Location", "/login?google=failed"));
    Flow flow = start(HOST);
    mvc.perform(
            get(CALLBACK)
                .header("Host", HOST)
                .session(flow.session())
                .param("state", "wrong")
                .param("code", "unused"))
        .andExpect(header().string("Location", "/login?google=failed"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email IS NOT NULL", Integer.class))
        .isZero();
  }

  @Test
  void bindsStateToSessionAndExpiresItAfterFiveMinutes() throws Exception {
    Flow flow = start(HOST);
    mvc.perform(
            get(CALLBACK)
                .header("Host", HOST)
                .session(new MockHttpSession())
                .param("state", flow.state())
                .param("code", "unused"))
        .andExpect(header().string("Location", "/login?google=failed"));
    now.set(now.get().plusSeconds(300));
    callback(flow, Map.of(), false).andExpect(header().string("Location", "/login?google=failed"));
    Flow fresh = start(HOST);
    callback(fresh, Map.of(), false).andExpect(header().string("Location", "/customer?checkout=1"));
    // A copied pre-login session cannot replay the authorization request after rotation.
    assertThat(fresh.session().isInvalid()).isTrue();
  }

  @Test
  void bothDomainsUseFixedHttpsCallbacksAndRejectHostOrReturnPathInjection() throws Exception {
    for (String host : java.util.List.of(HOST, "shopflow.phamthuy.dev")) {
      Flow flow = start(host);
      assertThat(flow.callback()).isEqualTo("https://" + host + "/api" + CALLBACK);
      assertThat(flow.state().length()).isGreaterThanOrEqualTo(22);
      assertThat(flow.challenge()).hasSize(43);
    }
    mvc.perform(
            get("/oauth2/authorization/google")
                .header("Host", "evil.example")
                .header("X-Forwarded-Host", HOST))
        .andExpect(status().isBadRequest());
    for (String path :
        java.util.List.of(
            "//evil.example",
            "https://evil.example",
            "/customer%0d%0a",
            "/warehouse",
            "/customer\\evil")) {
      mvc.perform(get("/oauth2/authorization/google").header("Host", HOST).param("returnTo", path))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void collisionRequiresTheExistingPasswordAndDoesNotChangeRoleOrOwnership() throws Exception {
    long id = local();
    Flow flow = start(HOST);
    MvcResult collision =
        callback(flow, Map.of(), false)
            .andExpect(header().string("Location", "/login?google=link_required"))
            .andReturn();
    MockHttpSession pending = session(collision);
    mvc.perform(get("/auth/session").session(pending))
        .andExpect(jsonPath("$.authenticated").value(false));
    link(pending, "wrong").andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT google_sub FROM shopflow.users WHERE id = ?", String.class, id))
        .isNull();
    link(new MockHttpSession(), PASSWORD).andExpect(status().isBadRequest());
    MvcResult linked =
        link(pending, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(header().string("Location", "/customer?checkout=1"))
            .andExpect(jsonPath("$.userId").value(id))
            .andExpect(jsonPath("$.role").value("WAREHOUSE"))
            .andReturn();
    assertThat(pending.isInvalid()).isTrue();
    link(session(linked), PASSWORD).andExpect(status().isBadRequest());
    callback(start(HOST), Map.of(), false)
        .andExpect(header().string("Location", "/customer?checkout=1"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email = ?", Integer.class, EMAIL))
        .isEqualTo(1);
  }

  @Test
  void linkRejectsExpiredProofCredentialChangesInactiveAccountsCsrfAndUnknownFields()
      throws Exception {
    local();
    MockHttpSession pending = session(callback(start(HOST), Map.of(), false).andReturn());
    mvc.perform(
            post("/auth/google/link")
                .session(pending)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("password", PASSWORD))))
        .andExpect(status().isForbidden());
    mvc.perform(
            write(post("/auth/google/link"), pending)
                .header("Origin", "https://evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("password", PASSWORD))))
        .andExpect(status().isForbidden());
    mvc.perform(
            write(post("/auth/google/link"), pending)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSON.writeValueAsString(
                        Map.of("password", PASSWORD, "email", "other@example.com"))))
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE shopflow.users SET auth_version = auth_version + 1 WHERE email = ?", EMAIL);
    link(pending, PASSWORD).andExpect(status().isBadRequest());
    pending = session(callback(start(HOST), Map.of(), false).andReturn());
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE email = ?", EMAIL);
    link(pending, PASSWORD).andExpect(status().isBadRequest());
    jdbc.update("UPDATE shopflow.users SET active = TRUE WHERE email = ?", EMAIL);
    pending = session(callback(start(HOST), Map.of(), false).andReturn());
    now.set(now.get().plusSeconds(300));
    link(pending, PASSWORD).andExpect(status().isBadRequest());
  }

  @Test
  void linkSharesLoginFailureBudget() throws Exception {
    local();
    MockHttpSession pending = session(callback(start(HOST), Map.of(), false).andReturn());
    for (int i = 0; i < 5; i++) link(pending, "wrong").andExpect(status().isUnauthorized());
    link(pending, PASSWORD)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
    assertThat(
            jdbc.queryForObject(
                "SELECT google_sub FROM shopflow.users WHERE email = ?", String.class, EMAIL))
        .isNull();
  }

  @Test
  void cancellationPreservesOnlySafeIntentForPasswordLogin() throws Exception {
    local();
    Flow flow = start(HOST);
    mvc.perform(
            get(CALLBACK)
                .header("Host", HOST)
                .session(flow.session())
                .param("state", flow.state())
                .param("error", "access_denied")
                .param("error_description", "do not reflect"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/login?google=failed"));
    mvc.perform(
            post("/auth/login")
                .session(flow.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("username", EMAIL, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(header().string("Location", "/customer?checkout=1"));
  }

  @Test
  void concurrentCallbacksCreateOneAccountAndConflictingLinksHaveOneWinner() throws Exception {
    Flow first = start(HOST), second = start("shopflow.phamthuy.dev");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = pool.submit(() -> callback(first, Map.of(), false).andReturn());
      var b = pool.submit(() -> callback(second, Map.of(), false).andReturn());
      assertThat(a.get().getResponse().getHeader("Location")).isEqualTo("/customer?checkout=1");
      assertThat(b.get().getResponse().getHeader("Location")).isEqualTo("/customer?checkout=1");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE email = ?", Integer.class, EMAIL))
        .isEqualTo(1);
    jdbc.update("DELETE FROM shopflow.users WHERE email = ?", EMAIL);
    local();
    MockHttpSession one =
        session(callback(start(HOST), Map.of("sub", "subject-a"), false).andReturn());
    MockHttpSession two =
        session(callback(start(HOST), Map.of("sub", "subject-b"), false).andReturn());
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = pool.submit(() -> link(one, PASSWORD).andReturn().getResponse().getStatus());
      var b = pool.submit(() -> link(two, PASSWORD).andReturn().getResponse().getStatus());
      assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 400);
    }
  }

  @Test
  void googleCreationInvalidatesPendingRegistrationWithoutTakingItsPassword() throws Exception {
    jdbc.update(
        """
        INSERT INTO shopflow.auth_email_challenges (email,purpose,challenge_id,session_binding,otp_verifier,
          synthetic,status,issued_at,expires_at,resend_at,pending_password_hash,pending_display_name)
        VALUES (?, 'REGISTER', ?, ?, ?, FALSE, 'ISSUED', ?, ?, ?, ?, 'Pending signup')
        """,
        EMAIL,
        UUID.randomUUID(),
        new byte[32],
        new byte[32],
        java.sql.Timestamp.from(now.get()),
        java.sql.Timestamp.from(now.get().plusSeconds(600)),
        java.sql.Timestamp.from(now.get().plusSeconds(60)),
        encoder.encode(PASSWORD));
    callback(start(HOST), Map.of(), false)
        .andExpect(header().string("Location", "/customer?checkout=1"));
    var challenge =
        jdbc.queryForMap(
            "SELECT status,pending_password_hash,otp_verifier FROM shopflow.auth_email_challenges WHERE email = ?",
            EMAIL);
    assertThat(challenge.get("status")).isEqualTo("EXPIRED");
    assertThat(challenge.get("pending_password_hash")).isNull();
    assertThat(challenge.get("otp_verifier")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM shopflow.users WHERE email = ?", String.class, EMAIL))
        .isNull();
  }

  @Test
  void callbackCannotChangeTheOriginThatStartedTheFlow() throws Exception {
    Flow flow = start(HOST);
    mvc.perform(
            get(CALLBACK)
                .header("Host", "shopflow.phamthuy.dev")
                .session(flow.session())
                .param("state", flow.state())
                .param("code", "unused"))
        .andExpect(header().string("Location", "/login?google=failed"));
  }

  @Test
  void inactiveAndDifferentSubjectGoogleOnlyAccountsCannotBeLinked() throws Exception {
    callback(start(HOST), Map.of(), false)
        .andExpect(header().string("Location", "/customer?checkout=1"));
    callback(start(HOST), Map.of("sub", "different-subject"), false)
        .andExpect(header().string("Location", "/login?google=failed"));
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE email = ?", EMAIL);
    callback(start(HOST), Map.of(), false)
        .andExpect(header().string("Location", "/login?google=failed"));
    jdbc.update("DELETE FROM shopflow.users WHERE email = ?", EMAIL);
    local();
    jdbc.update("UPDATE shopflow.users SET active = FALSE WHERE email = ?", EMAIL);
    callback(start(HOST), Map.of(), false)
        .andExpect(header().string("Location", "/login?google=failed"));
  }

  private long local() {
    return jdbc.queryForObject(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,password_hash,display_name,role)
        VALUES ('google-local', ?, CURRENT_TIMESTAMP, ?, 'Existing local', 'WAREHOUSE') RETURNING id
        """,
        Long.class,
        EMAIL,
        encoder.encode(PASSWORD));
  }

  private Flow start(String host) throws Exception {
    MvcResult result =
        mvc.perform(
                get("/oauth2/authorization/google")
                    .header("Host", host)
                    .header("X-Forwarded-Host", "evil.example")
                    .header("X-Forwarded-Proto", "http")
                    .param("returnTo", "/customer?checkout=1"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    Map<String, String> params =
        query(URI.create(result.getResponse().getHeader("Location")).getRawQuery());
    assertThat(params.get("scope").split(" "))
        .containsExactlyInAnyOrder("openid", "email", "profile");
    assertThat(params.get("code_challenge_method")).isEqualTo("S256");
    assertThat(params).doesNotContainKeys("client_secret", "access_type");
    return new Flow(
        session(result),
        host,
        params.get("state"),
        params.get("nonce"),
        params.get("code_challenge"),
        params.get("redirect_uri"));
  }

  private ResultActions callback(Flow flow, Map<String, Object> claims, boolean signature)
      throws Exception {
    String code = UUID.randomUUID().toString();
    CODES.put(code, new Token(flow, claims, signature));
    return mvc.perform(
        get(CALLBACK)
            .header("Host", flow.host())
            .session(flow.session())
            .param("state", flow.state())
            .param("code", code));
  }

  private ResultActions link(MockHttpSession session, String password) throws Exception {
    return mvc.perform(
        write(post("/auth/google/link"), session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("password", password))));
  }

  private MockHttpServletRequestBuilder write(
      MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
    Cookie token =
        mvc.perform(get("/auth/session").session(session))
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    return request.session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue());
  }

  private static MockHttpSession session(MvcResult result) {
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static Map<String, String> query(String query) {
    Map<String, String> values = new HashMap<>();
    for (String pair : query.split("&")) {
      String[] fields = pair.split("=", 2);
      values.put(
          URLDecoder.decode(fields[0], StandardCharsets.UTF_8),
          URLDecoder.decode(fields.length == 1 ? "" : fields[1], StandardCharsets.UTF_8));
    }
    return values;
  }

  private static void reply(HttpExchange exchange, int status, Object body)
      throws java.io.IOException {
    byte[] bytes = JSON.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private record Flow(
      MockHttpSession session,
      String host,
      String state,
      String nonce,
      String challenge,
      String callback) {}

  private record Token(Flow flow, Map<String, Object> claims, boolean wrongSignature) {}
}
