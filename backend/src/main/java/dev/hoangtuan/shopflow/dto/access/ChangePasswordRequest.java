package dev.hoangtuan.shopflow.dto.access;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(@NotNull String currentPassword, @NotNull String newPassword) {
  @JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
