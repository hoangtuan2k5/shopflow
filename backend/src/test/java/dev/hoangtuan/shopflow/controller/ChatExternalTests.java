package dev.hoangtuan.shopflow.controller;

import static org.assertj.core.api.Assertions.*;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@Tag("external")
@EnabledIfEnvironmentVariable(named = "CHAT_QA_BASE_URL", matches = "http://127\\.0\\.0\\.1:[0-9]+")
class ChatExternalTests {
  @Test
  void recordsExploratoryModelObservationsAgainstDisposableLocalBackend() throws Exception {
    String base = System.getenv("CHAT_QA_BASE_URL");
    var cookies = new CookieManager();
    var http =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    var json = JsonMapper.builder().build();
    assertThat(
            http.send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/session")).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
    String token =
        cookies.getCookieStore().getCookies().stream()
            .filter(c -> c.getName().equals("XSRF-TOKEN"))
            .findFirst()
            .orElseThrow()
            .getValue();
    var observations = new ArrayList<Map<String, Object>>();
    String fixture;
    try (var input = getClass().getResourceAsStream("/chat-adversarial.json")) {
      fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    try {
      for (var example : json.readTree(fixture)) {
        var payload =
            Map.of("question", example.get("question").asText(), "history", example.get("history"));
        long start = System.nanoTime();
        var request =
            HttpRequest.newBuilder(URI.create(base + "/chat/messages"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var observation =
            Map.<String, Object>of(
                "case",
                example.get("case").asText(),
                "question",
                example.get("question").asText(),
                "status",
                response.statusCode(),
                "seconds",
                (System.nanoTime() - start) / 1_000_000_000.0,
                "response",
                json.readTree(response.body()));
        observations.add(observation);
        System.out.println(json.writeValueAsString(observation));
        if (response.statusCode() == 200) {
          for (var p : json.readTree(response.body()).get("products")) {
            var detail =
                http.send(
                    HttpRequest.newBuilder(URI.create(base + "/products/" + p.get("id").asLong()))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(detail.statusCode()).isEqualTo(200);
            var current = json.readTree(detail.body());
            for (String field : new String[] {"id", "name", "price", "stockStatus", "imageUrl"})
              assertThat(p.get(field)).isEqualTo(current.get(field));
          }
        }
      }
    } finally {
      Files.writeString(
          Path.of(System.getProperty("chat.qa.output", "/tmp/sf76-external-observations.json")),
          json.writerWithDefaultPrettyPrinter().writeValueAsString(observations));
    }
  }
}
