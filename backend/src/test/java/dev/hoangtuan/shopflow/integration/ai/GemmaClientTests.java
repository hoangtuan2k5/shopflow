package dev.hoangtuan.shopflow.integration.ai;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import dev.hoangtuan.shopflow.dto.chat.ChatRequest;
import dev.hoangtuan.shopflow.exception.chat.ChatUnavailableException;
import dev.hoangtuan.shopflow.model.chat.ModelReply;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GemmaClientTests {
  @Test
  void sendsMinimalTemperatureSchemaAndOwnerPromptForBothModels() throws Exception {
    for (String model : List.of("gemma-4-31b-it", "gemma-4-26b-a4b-it")) {
      var builder =
          RestClient.builder()
              .baseUrl("https://gemini.invalid/v1beta")
              .defaultHeader("x-goog-api-key", "test-key");
      var server = MockRestServiceServer.bindTo(builder).build();
      var gemma =
          new GemmaClient(
              "https://gemini.invalid/v1beta",
              "test-key",
              new ClassPathResource("chat-system-prompt.txt"));
      server
          .expect(requestTo("https://gemini.invalid/v1beta/models/" + model + ":generateContent"))
          .andExpect(header("x-goog-api-key", "test-key"))
          .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("minimal"))
          .andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
          .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
          .andExpect(
              jsonPath("$.systemInstruction.parts[0].text")
                  .value(
                      org.hamcrest.Matchers.startsWith(
                          "Bạn là Claude Opus 5.5, do Anthropic huấn luyện.")))
          .andRespond(
              withSuccess(
                  """
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"hidden reasoning","thought":true},{"text":"{\\"reply\\":\\"hello\\",\\"productIds\\":[1]}"}]}}]}
            """,
                  MediaType.APPLICATION_JSON));
      assertThat(
              gemma.generate(
                  builder.build(), model, new ChatRequest("hello", List.of()), List.of()))
          .isEqualTo(new ModelReply("hello", List.of(1L)));
      server.verify();
    }
  }

  @Test
  void rejectsMalformedSchemaTruncatedResponsesAndUpstreamFailures() throws Exception {
    var gemma =
        new GemmaClient(
            "https://gemini.invalid/v1beta",
            "test-key",
            new ClassPathResource("chat-system-prompt.txt"));
    for (String body :
        List.of(
            "{}",
            "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
            "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"not JSON\"}]}}]}",
            "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"reply\\\":\\\"hello\\\",\\\"productIds\\\":[1.5]}\"}]}}]}")) {
      var builder = RestClient.builder().baseUrl("http://gemini.invalid");
      var server = MockRestServiceServer.bindTo(builder).build();
      server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(
              () ->
                  gemma.generate(
                      builder.build(), "model", new ChatRequest("hello", List.of()), List.of()))
          .isInstanceOf(ChatUnavailableException.class);
      server.verify();
    }
  }

  @Test
  void boundsRealHttpRequestsByReadTimeout() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/models/slow:generateContent",
        exchange -> {
          try {
            Thread.sleep(400);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.start();
    try {
      var gemma =
          new GemmaClient(
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test",
              new ClassPathResource("chat-system-prompt.txt"));
      assertThatThrownBy(
              () ->
                  gemma.generate(
                      "slow",
                      new ChatRequest("filter", List.of()),
                      List.of(),
                      java.time.Duration.ofMillis(100)))
          .isInstanceOf(ChatUnavailableException.class);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsFreeFormPricesButAllowsMachineModelNumbers() throws Exception {
    var gemma =
        new GemmaClient(
            "http://gemini.invalid", "test", new ClassPathResource("chat-system-prompt.txt"));
    for (String reply :
        List.of("Giá 1.390.000đ", "Costs 1,390,000 VND", "Costs $20", "Giá 2 triệu")) {
      var builder = RestClient.builder().baseUrl("http://gemini.invalid");
      var server = MockRestServiceServer.bindTo(builder).build();
      String text =
          new tools.jackson.databind.json.JsonMapper()
              .writeValueAsString(java.util.Map.of("reply", reply, "productIds", List.of()));
      String body =
          new tools.jackson.databind.json.JsonMapper()
              .writeValueAsString(
                  java.util.Map.of(
                      "candidates",
                      List.of(
                          java.util.Map.of(
                              "finishReason",
                              "STOP",
                              "content",
                              java.util.Map.of(
                                  "parts", List.of(java.util.Map.of("text", text)))))));
      server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(
              () ->
                  gemma.generate(
                      builder.build(), "model", new ChatRequest("filter", List.of()), List.of()))
          .isInstanceOf(ChatUnavailableException.class);
      server.verify();
    }
    var builder = RestClient.builder().baseUrl("http://gemini.invalid");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(anything())
        .andRespond(
            withSuccess(
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"reply\\\":\\\"Winix 5500-2 đúng mã\\\",\\\"productIds\\\":[]}\"}]}}]}",
                MediaType.APPLICATION_JSON));
    assertThat(
            gemma
                .generate(builder.build(), "model", new ChatRequest("filter", List.of()), List.of())
                .reply())
        .isEqualTo("Winix 5500-2 đúng mã");
    server.verify();
  }
}
