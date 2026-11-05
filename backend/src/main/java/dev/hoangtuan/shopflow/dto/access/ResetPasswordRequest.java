package dev.hoangtuan.shopflow.dto.access;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record ResetPasswordRequest(
    @NotNull UUID challengeId,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
    @NotNull
        @Schema(minLength = 8, description = "At most 72 UTF-8 bytes; registration password policy")
        String newPassword) {
  @JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
