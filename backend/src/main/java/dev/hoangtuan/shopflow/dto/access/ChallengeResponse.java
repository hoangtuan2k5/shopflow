package dev.hoangtuan.shopflow.dto.access;

import java.util.UUID;

public record ChallengeResponse(
    UUID challengeId, String message, int expiresInSeconds, int resendAfterSeconds) {
  public static ChallengeResponse issued(UUID id) {
    return new ChallengeResponse(
        id, "If eligible, a code will be sent to the supplied email", 600, 60);
  }
}
