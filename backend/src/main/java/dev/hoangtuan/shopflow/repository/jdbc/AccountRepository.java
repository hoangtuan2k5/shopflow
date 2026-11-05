package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.model.access.Role;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  private final JdbcTemplate jdbcTemplate;

  public AccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final String SELECT_ACTIVE_BY_USERNAME =
      """
      SELECT id, username, password_hash, display_name, role, auth_version
      FROM shopflow.users
      WHERE LOWER(username) = LOWER(?) AND active = TRUE AND email IS NULL AND password_hash IS NOT NULL
      """;

  public AccountData findActiveByUsername(String username) {
    List<AccountData> accounts =
        jdbcTemplate.query(
            SELECT_ACTIVE_BY_USERNAME,
            (rs, rowNum) ->
                new AccountData(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("display_name"),
                    Role.valueOf(rs.getString("role")),
                    rs.getLong("auth_version")),
            username);
    return accounts.isEmpty() ? null : accounts.getFirst();
  }

  public SessionState findSessionState(long userId) {
    List<SessionState> states =
        jdbcTemplate.query(
            "SELECT active, auth_version FROM shopflow.users WHERE id = ?",
            (rs, rowNum) -> new SessionState(rs.getBoolean("active"), rs.getLong("auth_version")),
            userId);
    return states.isEmpty() ? null : states.getFirst();
  }

  public record SessionState(boolean active, long authVersion) {}

  public record AccountData(
      long id,
      String username,
      String passwordHash,
      String displayName,
      Role role,
      long authVersion) {}
}
