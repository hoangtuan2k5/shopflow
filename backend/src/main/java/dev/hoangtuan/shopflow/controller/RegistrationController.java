package dev.hoangtuan.shopflow.controller;

import dev.hoangtuan.shopflow.dto.access.*;
import dev.hoangtuan.shopflow.service.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/auth")
public class RegistrationController {
  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @PostMapping("/registrations")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ChallengeResponse register(@RequestBody RegistrationRequest request, HttpServletRequest http) {
    return service.register(request, http.getSession(true).getId(), http.getRemoteAddr());
  }

  @PostMapping("/registrations/verify")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void verify(@Valid @RequestBody VerifyRegistrationRequest request, HttpServletRequest http) {
    var session = http.getSession(false);
    service.verify(
        request.challengeId(),
        request.otp(),
        session == null ? null : session.getId(),
        http.getRemoteAddr());
  }

  @PostMapping("/otp/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ChallengeResponse resend(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest http) {
    var session = http.getSession(false);
    return service.resend(
        request.challengeId(), session == null ? null : session.getId(), http.getRemoteAddr());
  }
}
