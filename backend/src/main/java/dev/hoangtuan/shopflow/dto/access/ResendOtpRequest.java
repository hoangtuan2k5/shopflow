package dev.hoangtuan.shopflow.dto.access;

public record ResendOtpRequest(@jakarta.validation.constraints.NotNull java.util.UUID challengeId) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
