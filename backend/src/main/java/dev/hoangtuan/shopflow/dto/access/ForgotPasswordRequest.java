package dev.hoangtuan.shopflow.dto.access;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

public record ForgotPasswordRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254) String email) {
  @JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
