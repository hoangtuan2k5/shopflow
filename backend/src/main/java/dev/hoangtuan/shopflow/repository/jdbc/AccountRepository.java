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
      SELECT id, username, password_hash, display_name, role
      FROM shopflow.users
      WHERE LOWER(username) = LOWER(?) AND active = TRUE
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
                    Role.valueOf(rs.getString("role"))),
            username);
    return accounts.isEmpty() ? null : accounts.getFirst();
  }

  public record AccountData(
      long id, String username, String passwordHash, String displayName, Role role) {}
}
