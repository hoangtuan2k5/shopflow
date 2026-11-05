package dev.hoangtuan.shopflow.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ResendEmailClientTests {
  private static final String MESSAGE_ID = "49a3999c-0ce1-4ea6-ab68-afcd6dc2e794";
  private HttpServer server;
  private java.util.concurrent.ExecutorService executor;
  private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
  private int status = 200;
  private String response = "{\"id\":\"" + MESSAGE_ID + "\"}";
  private int headerDelayMillis;
  private int bodyDelayMillis;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/emails",
        exchange -> {
          try {
            requests.add(
                new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new JsonMapper().readTree(exchange.getRequestBody().readAllBytes())));
            if (headerDelayMillis > 0) Thread.sleep(headerDelayMillis);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            if (bodyDelayMillis > 0) {
              exchange.getResponseBody().write(body, 0, 1);
              exchange.getResponseBody().flush();
              Thread.sleep(bodyDelayMillis);
              exchange.getResponseBody().write(body, 1, body.length - 1);
            } else exchange.getResponseBody().write(body);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } catch (java.io.IOException ignored) {
            // The timeout test deliberately disconnects before the response completes.
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
    executor.shutdownNow();
  }

  @Test
  void sendsBothCodePurposesWithEscapedHtmlAndStableIdempotency() {
    var client = client("fake-resend-key", "ShopFlow <shopflow@hoangtuan.dev>");
    UUID registration = UUID.randomUUID();
    assertThat(
            client.sendRegistrationCode(
                "buyer@example.com", "<img src=x onerror=alert(1)>&", "012345", registration))
        .isEqualTo(MESSAGE_ID);
    Request request = requests.remove();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.authorization()).isEqualTo("Bearer fake-resend-key");
    assertThat(request.idempotency()).isEqualTo(registration.toString());
    assertThat(request.contentType()).startsWith("application/json");
    assertThat(request.body().get("from").asText()).isEqualTo("ShopFlow <shopflow@hoangtuan.dev>");
    assertThat(request.body().get("to").get(0).asText()).isEqualTo("buyer@example.com");
    assertThat(request.body().get("subject").asText()).contains("Verify email");
    assertThat(request.body().get("text").asText())
        .contains("012345", "10 phút", "60 giây", "10 minutes", "once");
    assertThat(request.body().get("html").asText())
        .contains("&lt;img", "&gt;&amp;", "012345")
        .doesNotContain("<img");
    UUID reset = UUID.randomUUID();
    client.sendResetCode("buyer@example.com", "654321", reset);
    request = requests.remove();
    assertThat(request.idempotency()).isEqualTo(reset.toString());
    assertThat(request.body().get("subject").asText()).contains("Reset password");
    assertThat(request.body().get("text").asText()).contains("654321", "ignore this email");
  }

  @Test
  void sendsPasswordChangedNoticeWithoutCredentials() {
    UUID generation = UUID.randomUUID();
    client("fake-key", "ShopFlow <shopflow@hoangtuan.dev>")
        .sendPasswordChanged("buyer@example.com", generation);
    Request request = requests.remove();
    assertThat(request.idempotency()).isEqualTo("password-changed/" + generation);
    assertThat(request.body().get("subject").asText()).contains("Password changed");
    assertThat(request.body().get("text").asText())
        .contains("contact ShopFlow support")
        .doesNotContain("Verification code:");
    assertThat(request.body().get("html").asText()).contains("<h1>ShopFlow</h1>");
  }

  @Test
  void rejectsMissingConfigurationWithoutMakingRequests() {
    for (String[] config :
        List.of(new String[] {"", "sender@example.com"}, new String[] {"key", " "})) {
      var client = client(config[0], config[1]);
      assertThatThrownBy(client::requireConfigured).isInstanceOf(EmailUnavailableException.class);
      assertThatThrownBy(
              () -> client.sendResetCode("buyer@example.com", "012345", UUID.randomUUID()))
          .isInstanceOf(EmailUnavailableException.class);
    }
    assertThat(requests).isEmpty();
  }

  @Test
  void doesNotRetryOrExposeProviderErrorsAndSecrets(CapturedOutput output) {
    for (int failure : new int[] {301, 400, 401, 403, 429, 500, 503}) {
      status = failure;
      response =
          "{\"message\":\"sensitive-provider-body fake-resend-key 012345 buyer@example.com\"}";
      Throwable error =
          catchThrowable(
              () ->
                  client("fake-resend-key", "sender@example.com")
                      .sendResetCode("buyer@example.com", "012345", UUID.randomUUID()));
      assertThat(error)
          .isInstanceOf(EmailUnavailableException.class)
          .hasMessage("Authentication service unavailable")
          .hasNoCause();
      assertThat(requests).hasSize(1);
      requests.clear();
    }
    assertThat(output.getAll())
        .doesNotContain(
            "sensitive-provider-body", "fake-resend-key", "012345", "buyer@example.com");
  }

  @Test
  void rejectsMalformedSuccessResponses() {
    for (String body : List.of("{}", "null", "{\"id\":\"\"}", "{\"id\":\"invalid\"}", "not-json")) {
      response = body;
      assertThatThrownBy(
              () ->
                  client("fake-key", "sender@example.com")
                      .sendResetCode("buyer@example.com", "012345", UUID.randomUUID()))
          .isInstanceOf(EmailUnavailableException.class)
          .hasNoCause();
    }
  }

  @Test
  void boundsSlowHeadersAndSlowResponseBody() {
    for (int[] delays : new int[][] {{4500, 0}, {0, 4500}, {1800, 3000}}) {
      headerDelayMillis = delays[0];
      bodyDelayMillis = delays[1];
      var client = client("fake-key", "sender@example.com");
      long start = System.nanoTime();
      assertThatThrownBy(
              () -> client.sendResetCode("buyer@example.com", "012345", UUID.randomUUID()))
          .isInstanceOf(EmailUnavailableException.class)
          .hasNoCause();
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }
  }

  @Test
  void rejectsInvalidCodesBeforeSending() {
    for (String code : List.of("12345", "1234567", "abcdef", "１２３４５６")) {
      assertThatThrownBy(
              () ->
                  client("key", "sender@example.com")
                      .sendResetCode("buyer@example.com", code, UUID.randomUUID()))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(requests).isEmpty();
  }

  private ResendEmailClient client(String key, String from) {
    return new ResendEmailClient(
        RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), key, from);
  }

  private record Request(
      String method, String authorization, String idempotency, String contentType, JsonNode body) {}
}
