package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.chat.ChatRequest;
import dev.hoangtuan.shopflow.dto.chat.ChatResponse;
import dev.hoangtuan.shopflow.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
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
