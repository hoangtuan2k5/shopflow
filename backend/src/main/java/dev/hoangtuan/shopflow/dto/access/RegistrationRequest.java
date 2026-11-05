package dev.hoangtuan.shopflow.dto.access;

public record RegistrationRequest(
    @io.swagger.v3.oas.annotations.media.Schema(
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
            maxLength = 254)
        String email,
    @io.swagger.v3.oas.annotations.media.Schema(
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
            minLength = 8,
            description =
                "At most 72 UTF-8 bytes; uppercase, lowercase, ASCII digit and punctuation/symbol required")
        String password,
    @io.swagger.v3.oas.annotations.media.Schema(
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 255)
        String displayName) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
