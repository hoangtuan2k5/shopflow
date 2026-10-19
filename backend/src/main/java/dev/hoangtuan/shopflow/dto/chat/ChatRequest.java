package dev.hoangtuan.shopflow.dto.chat;

import java.util.List;

public record ChatRequest(String question, List<Message> history) {
  public ChatRequest validated() {
    if (question == null || question.isBlank() || question.length() > 500)
      throw new IllegalArgumentException();
    var messages =
        history == null
            ? List.<Message>of()
            : history.subList(Math.max(0, history.size() - 6), history.size());
    if (messages.stream()
        .anyMatch(
            m ->
                m == null
                    || !("user".equals(m.role()) || "assistant".equals(m.role()))
                    || m.content() == null
                    || m.content().isBlank()
                    || m.content().length() > 2000)) throw new IllegalArgumentException();
    return new ChatRequest(question.strip(), List.copyOf(messages));
  }

  public record Message(String role, String content) {}
}
