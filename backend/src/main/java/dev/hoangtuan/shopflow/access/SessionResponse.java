package dev.hoangtuan.shopflow.access;

record SessionResponse(boolean authenticated, AuthenticatedUser user) {

  static SessionResponse anonymous() {
    return new SessionResponse(false, null);
  }

  static SessionResponse of(AuthenticatedUser user) {
    return new SessionResponse(true, user);
  }
}
