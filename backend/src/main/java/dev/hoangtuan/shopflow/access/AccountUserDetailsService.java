package dev.hoangtuan.shopflow.access;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class AccountUserDetailsService implements UserDetailsService {

  private static final String SELECT_ACTIVE_BY_USERNAME =
      """
      SELECT id, username, password_hash, display_name, role
      FROM shopflow.users
      WHERE LOWER(username) = LOWER(?) AND active = TRUE
      """;

  private final JdbcTemplate jdbcTemplate;

  AccountUserDetailsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Tài khoản bị khóa được coi như không tồn tại. Nhờ đó ba trường hợp sai định danh, sai mật khẩu
   * và tài khoản bị khóa đi cùng một nhánh xử lý, cùng một exception và cùng thời gian phản hồi —
   * {@code DaoAuthenticationProvider} vẫn chạy một phép so khớp giả khi không tìm thấy tài khoản.
   */
  @Override
  public UserDetails loadUserByUsername(String username) {
    List<AccountPrincipal> accounts =
        jdbcTemplate.query(
            SELECT_ACTIVE_BY_USERNAME,
            (rs, rowNum) ->
                new AccountPrincipal(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("display_name"),
                    Role.valueOf(rs.getString("role"))),
            username);
    if (accounts.isEmpty()) {
      throw new UsernameNotFoundException("Account not found");
    }
    return accounts.getFirst();
  }
}
