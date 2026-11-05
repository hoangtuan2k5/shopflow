package dev.hoangtuan.shopflow.dto.access;

public record VerifyRegistrationRequest(
    @jakarta.validation.constraints.NotNull java.util.UUID challengeId,
    @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}")
        String otp) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
