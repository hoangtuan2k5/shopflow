package dev.hoangtuan.shopflow.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hoangtuan.shopflow.ShopflowApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PersistentSessionTests {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Test
  void loginSurvivesBackendRestartAndLogoutInvalidatesOldCookie() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    String cookie;
    try (var app = start()) {
      int port = port(app);
      var login =
          send(
              client,
              port,
              "POST",
              "/auth/login",
              null,
              null,
              "{\"username\":\"owner\",\"password\":\"Owner@2026\"}");
      assertThat(login.statusCode()).isEqualTo(200);
      assertThat(
              login.headers().allValues("set-cookie").stream()
                  .filter(value -> value.startsWith("SESSION="))
                  .findFirst()
                  .orElseThrow())
          .containsIgnoringCase("httponly")
          .containsIgnoringCase("samesite=lax");
      cookie =
          login.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":true");
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  "SELECT attribute_bytes FROM shopflow.spring_session_attributes")) {
        while (rows.next()) {
          assertThat(new String(rows.getBytes(1), java.nio.charset.StandardCharsets.ISO_8859_1))
              .doesNotContain("$2a$", "$2b$");
        }
      }
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  "SELECT max_inactive_interval FROM shopflow.spring_session WHERE principal_name = 'owner'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(8 * 60 * 60);
      }
    }
    try (var app = start()) {
      int port = port(app);
      var session = send(client, port, "GET", "/auth/session", cookie, null, null);
      assertThat(session.statusCode()).isEqualTo(200);
      assertThat(session.body()).contains("\"authenticated\":true", "owner", "SHOP_OWNER");
      String csrf =
          session.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("XSRF-TOKEN="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      assertThat(send(client, port, "POST", "/receivings", cookie, null, "{}").statusCode())
          .isIn(401, 403);
      assertThat(
              send(
                      client,
                      port,
                      "POST",
                      "/receivings",
                      cookie + "; " + csrf,
                      csrf.substring("XSRF-TOKEN=".length()),
                      "{}")
                  .statusCode())
          .isEqualTo(400);
      long productId;
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement();
          var rows =
              statement.executeQuery("SELECT id FROM shopflow.products ORDER BY id LIMIT 1")) {
        assertThat(rows.next()).isTrue();
        productId = rows.getLong(1);
      }
      var changed =
          send(
              client,
              port,
              "PUT",
              "/inventory/" + productId + "/threshold",
              cookie + "; " + csrf,
              csrf.substring("XSRF-TOKEN=".length()),
              "{\"lowStockThreshold\":7}");
      assertThat(changed.statusCode()).isEqualTo(200);
      assertThat(changed.body()).contains("\"lowStockThreshold\":7");
      assertThat(send(client, port, "GET", "/products", null, null, null).statusCode())
          .isEqualTo(200);
      assertThat(send(client, port, "POST", "/orders", null, null, "{}").statusCode())
          .isIn(401, 403);
      var logout =
          send(
              client,
              port,
              "POST",
              "/auth/logout",
              cookie + "; " + csrf,
              csrf.substring("XSRF-TOKEN=".length()),
              "");
      assertThat(logout.statusCode()).isEqualTo(204);
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":false");
    }
  }

  @Test
  void expiredStoredSessionCannotAuthenticate() throws Exception {
    var client = HttpClient.newHttpClient();
    try (var app = start()) {
      int port = port(app);
      var login =
          send(
              client,
              port,
              "POST",
              "/auth/login",
              null,
              null,
              "{\"username\":\"owner\",\"password\":\"Owner@2026\"}");
      assertThat(login.statusCode()).isEqualTo(200);
      String cookie =
          login.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement()) {
        assertThat(
                statement.executeUpdate(
                    "UPDATE shopflow.spring_session SET last_access_time = 0, expiry_time = 0 WHERE principal_name = 'owner'"))
            .isPositive();
      }
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":false");
    }
  }

  @Test
  void productionCookieKeepsSecurityAttributes() throws Exception {
    try (var app = start("--server.servlet.session.cookie.secure=true")) {
      var login =
          send(
              HttpClient.newHttpClient(),
              port(app),
              "POST",
              "/auth/login",
              null,
              null,
              "{\"username\":\"owner\",\"password\":\"Owner@2026\"}");
      assertThat(login.statusCode()).isEqualTo(200);
      String cookie =
          login.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .findFirst()
              .orElseThrow();
      assertThat(cookie)
          .containsIgnoringCase("secure")
          .containsIgnoringCase("httponly")
          .containsIgnoringCase("samesite=lax")
          .doesNotContain("Domain=");
    }
  }

  @Test
  void registrationOtpRemainsBoundToSessionAfterRestart() throws Exception {
    var mail =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    var code = new java.util.concurrent.atomic.AtomicReference<String>();
    mail.createContext(
        "/emails",
        exchange -> {
          try {
            var body =
                new tools.jackson.databind.json.JsonMapper().readTree(exchange.getRequestBody());
            var matcher =
                java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])")
                    .matcher(body.path("text").asString());
            if (!matcher.find()) throw new IllegalStateException("OTP missing from test email");
            code.set(matcher.group());
            byte[] reply =
                "{\"id\":\"00000000-0000-4000-8000-000000000001\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
          } finally {
            exchange.close();
          }
        });
    mail.start();
    String[] config = {
      "--shopflow.auth.otp-hmac-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "--shopflow.mail.base-url=http://127.0.0.1:" + mail.getAddress().getPort(),
      "--shopflow.mail.api-key=test-only",
      "--shopflow.mail.from=shop@example.test"
    };
    var client = HttpClient.newHttpClient();
    try {
      String cookie;
      String csrf;
      String challenge;
      try (var app = start(config)) {
        int port = port(app);
        var bootstrap = send(client, port, "GET", "/auth/session", null, null, null);
        csrf =
            bootstrap.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
        var issued =
            send(
                client,
                port,
                "POST",
                "/auth/registrations",
                csrf,
                csrf.substring("XSRF-TOKEN=".length()),
                "{\"email\":\"restart@example.test\",\"password\":\"Abcdef1!\",\"displayName\":\"Buyer\"}");
        assertThat(issued.statusCode()).isEqualTo(202);
        cookie =
            issued.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
        challenge =
            new tools.jackson.databind.json.JsonMapper()
                .readTree(issued.body())
                .path("challengeId")
                .asString();
        assertThat(code.get()).matches("[0-9]{6}");
      }
      try (var app = start(config)) {
        int port = port(app);
        var verify =
            send(
                client,
                port,
                "POST",
                "/auth/registrations/verify",
                cookie + "; " + csrf,
                csrf.substring("XSRF-TOKEN=".length()),
                "{\"challengeId\":\"" + challenge + "\",\"otp\":\"" + code.get() + "\"}");
        assertThat(verify.statusCode()).isEqualTo(204);
        var login =
            send(
                client,
                port,
                "POST",
                "/auth/login",
                null,
                null,
                "{\"username\":\"restart@example.test\",\"password\":\"Abcdef1!\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        cookie =
            login.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
      }
      try (var app = start(config)) {
        assertThat(send(client, port(app), "GET", "/auth/session", cookie, null, null).body())
            .contains("\"authenticated\":true", "CUSTOMER");
      }
    } finally {
      mail.stop(0);
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void pendingGoogleLinkSurvivesRestartAndRotatesSession() throws Exception {
    String cookie;
    var client = HttpClient.newHttpClient();
    try (var app = start()) {
      long id;
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement =
              connection.prepareStatement(
                  "INSERT INTO shopflow.users(username,password_hash,display_name,role,email,email_verified_at) VALUES (?,?,?,'CUSTOMER',?,CURRENT_TIMESTAMP) RETURNING id")) {
        statement.setString(1, "link-test");
        statement.setString(
            2,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("Existing1!"));
        statement.setString(3, "Link test");
        statement.setString(4, "link@example.test");
        try (var rows = statement.executeQuery()) {
          rows.next();
          id = rows.getLong(1);
        }
      }
      org.springframework.session.SessionRepository repository =
          app.getBean(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);
      org.springframework.session.Session session = repository.createSession();
      session.setAttribute(
          dev.hoangtuan.shopflow.security.GoogleFlowSession.class.getName() + ".link",
          new dev.hoangtuan.shopflow.security.GoogleFlowSession.PendingLink(
              "test-subject",
              "link@example.test",
              id,
              0,
              "/customer",
              java.time.Instant.now().plusSeconds(300)));
      repository.save(session);
      cookie =
          "SESSION="
              + java.util.Base64.getEncoder()
                  .encodeToString(
                      session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    try (var app = start()) {
      int port = port(app);
      var bootstrap = send(client, port, "GET", "/auth/session", cookie, null, null);
      String csrf =
          bootstrap.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("XSRF-TOKEN="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      var linked =
          send(
              client,
              port,
              "POST",
              "/auth/google/link",
              cookie + "; " + csrf,
              csrf.substring("XSRF-TOKEN=".length()),
              "{\"password\":\"Existing1!\"}");
      assertThat(linked.statusCode()).isEqualTo(200);
      String newCookie =
          linked.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      assertThat(newCookie).isNotEqualTo(cookie);
      assertThat(send(client, port, "GET", "/auth/session", newCookie, null, null).body())
          .contains("\"authenticated\":true", "CUSTOMER");
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":false");
    }
  }

  @Test
  void googleAuthorizationStateSurvivesRestart() throws Exception {
    String cookie;
    String state;
    try (var app = start()) {
      String response =
          googleRequest(port(app), "/oauth2/authorization/google?returnTo=%2Fcustomer", null);
      assertThat(response).startsWith("HTTP/1.1 302");
      cookie =
          response
              .lines()
              .filter(line -> line.toLowerCase().startsWith("set-cookie: session="))
              .findFirst()
              .orElseThrow()
              .substring("set-cookie: ".length())
              .split(";", 2)[0];
      String location =
          response
              .lines()
              .filter(line -> line.toLowerCase().startsWith("location: "))
              .findFirst()
              .orElseThrow()
              .substring("location: ".length());
      state =
          java.util.Arrays.stream(URI.create(location).getRawQuery().split("&"))
              .filter(part -> part.startsWith("state="))
              .findFirst()
              .orElseThrow()
              .substring(6);
      assertThat(authorizationRequestCount()).isEqualTo(1);
    }
    try (var app = start()) {
      String response =
          googleRequest(
              port(app), "/login/oauth2/code/google?error=access_denied&state=" + state, cookie);
      assertThat(response).startsWith("HTTP/1.1 303");
      assertThat(authorizationRequestCount()).isZero();
    }
  }

  private static long authorizationRequestCount() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT count(*) FROM shopflow.spring_session_attributes WHERE attribute_name LIKE '%AUTHORIZATION_REQUEST%'")) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static String googleRequest(int port, String path, String cookie) throws Exception {
    try (var socket = new java.net.Socket("127.0.0.1", port)) {
      socket.setSoTimeout(10000);
      String headers =
          "GET "
              + path
              + " HTTP/1.1\r\nHost: localhost:5173\r\nConnection: close\r\n"
              + (cookie == null ? "" : "Cookie: " + cookie + "\r\n")
              + "\r\n";
      socket.getOutputStream().write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      return new String(
          socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  @Test
  void credentialVersionChangeInvalidatesStoredSession() throws Exception {
    var client = HttpClient.newHttpClient();
    try (var app = start()) {
      int port = port(app);
      var login =
          send(
              client,
              port,
              "POST",
              "/auth/login",
              null,
              null,
              "{\"username\":\"warehouse\",\"password\":\"Warehouse@2026\"}");
      assertThat(login.statusCode()).isEqualTo(200);
      String cookie =
          login.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            "UPDATE shopflow.users SET auth_version = auth_version + 1 WHERE username = 'warehouse'");
      }
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":false");
    }
  }

  @Test
  void sessionSurvivesSeparateJvmRestart() throws Exception {
    var client = HttpClient.newHttpClient();
    int port;
    try (var socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var output = java.nio.file.Files.createTempFile("shopflow-session-process-", ".log");
    Process process = null;
    try {
      process = startProcess(port, output);
      waitUntilReady(client, port, process);
      var login =
          send(
              client,
              port,
              "POST",
              "/auth/login",
              null,
              null,
              "{\"username\":\"owner\",\"password\":\"Owner@2026\"}");
      assertThat(login.statusCode()).isEqualTo(200);
      String cookie =
          login.headers().allValues("set-cookie").stream()
              .filter(value -> value.startsWith("SESSION="))
              .map(value -> value.split(";", 2)[0])
              .findFirst()
              .orElseThrow();
      process.destroy();
      assertThat(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      process = startProcess(port, output);
      waitUntilReady(client, port, process);
      assertThat(send(client, port, "GET", "/auth/session", cookie, null, null).body())
          .contains("\"authenticated\":true", "SHOP_OWNER");
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
        process.waitFor();
      }
      java.nio.file.Files.deleteIfExists(output);
    }
  }

  private static Process startProcess(int port, java.nio.file.Path output) throws Exception {
    return new ProcessBuilder(
            java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("surefire.test.class.path"),
            ShopflowApplication.class.getName(),
            "--spring.profiles.active=test",
            "--spring.autoconfigure.exclude=",
            "--server.port=" + port,
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start();
  }

  private static void waitUntilReady(HttpClient client, int port, Process process)
      throws Exception {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < deadline && process.isAlive()) {
      try {
        if (send(client, port, "GET", "/actuator/health", null, null, null).statusCode() == 200)
          return;
      } catch (java.io.IOException ignored) {
      }
      Thread.sleep(250);
    }
    throw new IllegalStateException("Backend process did not become ready");
  }

  private static ConfigurableApplicationContext start(String... extra) {
    var args =
        new java.util.ArrayList<>(
            java.util.List.of(
                "--spring.profiles.active=test",
                "--spring.autoconfigure.exclude=",
                "--shopflow.auth.google.client-id=test-client",
                "--shopflow.auth.google.client-secret=test-secret",
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"));
    args.addAll(java.util.List.of(extra));
    return new SpringApplicationBuilder(ShopflowApplication.class).run(args.toArray(String[]::new));
  }

  private static int port(ConfigurableApplicationContext app) {
    return ((ServletWebServerApplicationContext) app).getWebServer().getPort();
  }

  private static HttpResponse<String> send(
      HttpClient client,
      int port,
      String method,
      String path,
      String cookie,
      String csrf,
      String body)
      throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (cookie != null) request.header("Cookie", cookie);
    if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
    if (body != null) request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
