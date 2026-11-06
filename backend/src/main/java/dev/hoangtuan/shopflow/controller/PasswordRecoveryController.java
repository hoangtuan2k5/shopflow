package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.access.ChallengeResponse;
import dev.hoangtuan.shopflow.dto.access.ForgotPasswordRequest;
import dev.hoangtuan.shopflow.dto.access.ResetPasswordRequest;
import dev.hoangtuan.shopflow.service.CustomerOtpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/auth/password")
public class PasswordRecoveryController {
  private final CustomerOtpService service;

  public PasswordRecoveryController(CustomerOtpService service) {
    this.service = service;
  }

  @PostMapping("/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ChallengeResponse forgot(@RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
    return service.forgot(request.email(), http.getSession(true).getId(), http.getRemoteAddr());
  }

  @PostMapping("/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
    var session = http.getSession(false);
    service.reset(
        request.challengeId(),
        request.otp(),
        request.newPassword(),
        session == null ? null : session.getId(),
        http.getRemoteAddr());
  }
}
