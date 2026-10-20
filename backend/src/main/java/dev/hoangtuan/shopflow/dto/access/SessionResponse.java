package dev.hoangtuan.shopflow.dto.access;

public record SessionResponse(boolean authenticated, AuthenticatedUser user) {

  public static SessionResponse anonymous() {
    return new SessionResponse(false, null);
  }

  public static SessionResponse of(AuthenticatedUser user) {
    return new SessionResponse(true, user);
  }
}
