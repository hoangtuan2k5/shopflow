package dev.hoangtuan.shopflow.chat;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {
  private final ChatService service;

  ChatController(ChatService service) {
    this.service = service;
  }

  @PostMapping("/chat/messages")
  ChatResponse send(@RequestBody ChatRequest request) {
    if (request == null) throw new IllegalArgumentException();
    return service.send(request.validated());
  }
}
