package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.receiving.ReceivingRequest;
import dev.hoangtuan.shopflow.dto.receiving.ReceivingResponse;
import dev.hoangtuan.shopflow.service.ReceivingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Receiving")
@RestController
@RequestMapping("/receivings")
public class ReceivingController {

  private final ReceivingService receivingService;

  ReceivingController(ReceivingService receivingService) {
    this.receivingService = receivingService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ReceivingResponse receive(@Valid @RequestBody ReceivingRequest request) {
    return receivingService.receive(request);
  }
}
