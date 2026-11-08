package dev.hoangtuan.shopflow.dto.access;

public record AccountProfile(String displayName, String email, Capabilities capabilities) {
  public record Capabilities(boolean manageDelivery, boolean changePassword, boolean setPassword) {}
}
