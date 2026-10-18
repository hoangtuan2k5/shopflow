package dev.hoangtuan.shopflow.chat;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
class GemmaClient {
  private static final java.util.regex.Pattern MONEY =
      java.util.regex.Pattern.compile(
          "(?:\\d[\\d\\s.,]*\\s*(?:₫|\\$|(?:đ|đồng|vnd|vnđ|usd|triệu|nghìn|ngàn|million|thousand)(?!\\p{L})))|(?:(?:\\$|vnd|vnđ|usd)\\s*\\d)",
          java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

  private final String baseUrl;
  private final String key;
  private final String prompt;
  private final JsonMapper json =
      JsonMapper.builder()
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
          .build();

  GemmaClient(
      @Value("${shopflow.gemini.base-url}") String baseUrl,
      @Value("${shopflow.gemini.api-key}") String key,
      @Value("classpath:chat-system-prompt.txt") Resource prompt)
      throws java.io.IOException {
    this.baseUrl = baseUrl;
    this.key = key;
    this.prompt = prompt.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  }

  void requireKey() {
    if (key.isBlank()) throw new ChatUnavailableException();
  }

  ModelReply generate(String model, ChatRequest request, List<?> candidates, Duration timeout) {
    requireKey();
    var http =
        HttpClient.newBuilder()
            .connectTimeout(
                timeout.compareTo(Duration.ofSeconds(2)) < 0 ? timeout : Duration.ofSeconds(2))
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    return generate(
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-goog-api-key", key)
            .requestFactory(factory)
            .build(),
        model,
        request,
        candidates);
  }

  ModelReply generate(RestClient client, String model, ChatRequest request, List<?> candidates) {
    try {
      var schema =
          Map.of(
              "type",
              "OBJECT",
              "properties",
              Map.of(
                  "reply",
                  Map.of("type", "STRING"),
                  "productIds",
                  Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER"))),
              "required",
              List.of("reply", "productIds"));
      var input =
          json.writeValueAsString(
              Map.of(
                  "QUESTION",
                  request.question(),
                  "HISTORY",
                  request.history(),
                  "CATALOG_CANDIDATES",
                  candidates));
      var body =
          Map.of(
              "systemInstruction",
              Map.of("parts", List.of(Map.of("text", prompt))),
              "contents",
              List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input)))),
              "generationConfig",
              Map.of(
                  "temperature",
                  0.2,
                  "maxOutputTokens",
                  800,
                  "thinkingConfig",
                  Map.of("thinkingLevel", "minimal"),
                  "responseMimeType",
                  "application/json",
                  "responseSchema",
                  schema));
      var response =
          client
              .post()
              .uri("/models/" + model + ":generateContent")
              .body(body)
              .retrieve()
              .body(Generation.class);
      if (response == null || response.candidates() == null || response.candidates().isEmpty())
        throw new ChatUnavailableException();
      var choice = response.candidates().getFirst();
      if (!"STOP".equals(choice.finishReason())
          || choice.content() == null
          || choice.content().parts() == null) throw new ChatUnavailableException();
      String text =
          choice.content().parts().stream()
              .filter(p -> !Boolean.TRUE.equals(p.thought()))
              .map(Part::text)
              .filter(java.util.Objects::nonNull)
              .collect(java.util.stream.Collectors.joining());
      var reply = json.readValue(text, ModelReply.class);
      if (reply.reply() == null
          || reply.reply().isBlank()
          || reply.reply().length() > 4000
          || MONEY.matcher(reply.reply()).find()
          || reply.productIds() == null
          || reply.productIds().size() > 20
          || reply.productIds().stream().anyMatch(id -> id == null || id <= 0))
        throw new ChatUnavailableException();
      return reply;
    } catch (RuntimeException e) {
      // Never surface upstream bodies, request content or credentials in public errors.
      throw new ChatUnavailableException();
    }
  }

  record ModelReply(String reply, List<Long> productIds) {}

  private record Generation(List<Candidate> candidates) {}

  private record Candidate(String finishReason, Content content) {}

  private record Content(List<Part> parts) {}

  private record Part(String text, Boolean thought) {}
}
