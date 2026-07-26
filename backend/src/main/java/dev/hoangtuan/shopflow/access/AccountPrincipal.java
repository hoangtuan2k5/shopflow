package dev.hoangtuan.shopflow.access;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Tài khoản đã tải từ database, dùng làm principal của Spring Security.
 *
 * <p>Chỉ tài khoản đang hoạt động mới được tạo principal, nên mọi cờ trạng thái của {@link
 * UserDetails} đều trả {@code true}: tài khoản bị khóa được xử lý như không tồn tại để phản hồi từ
 * chối không phân biệt được nguyên nhân (NFR-10).
 */
final class AccountPrincipal implements UserDetails {

  private final long userId;
  private final String username;
  private final String passwordHash;
  private final String displayName;
  private final Role role;

  AccountPrincipal(
      long userId, String username, String passwordHash, String displayName, Role role) {
    this.userId = userId;
    this.username = username;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.role = role;
  }

  AuthenticatedUser toAuthenticatedUser() {
    return new AuthenticatedUser(userId, username, displayName, role);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }
}
