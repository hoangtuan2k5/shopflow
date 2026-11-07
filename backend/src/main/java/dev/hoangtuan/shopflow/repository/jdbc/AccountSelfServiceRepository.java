package dev.hoangtuan.shopflow.repository.jdbc;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountSelfServiceRepository {
  private final JdbcTemplate jdbc;

  public AccountSelfServiceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Account find(long id, boolean lock) {
    List<Account> rows =
        jdbc.query(
            """
        SELECT id, username, display_name, email, email_verified_at IS NOT NULL AS verified,
          password_hash, google_sub IS NOT NULL AS google, active, auth_version
        FROM shopflow.users WHERE id = ?
        """
                + (lock ? " FOR UPDATE" : ""),
            (rs, n) ->
                new Account(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getBoolean("verified"),
                    rs.getString("password_hash"),
                    rs.getBoolean("google"),
                    rs.getBoolean("active"),
                    rs.getLong("auth_version")),
            id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void changePassword(long id, String hash, java.time.Instant now) {
    jdbc.update(
        "UPDATE shopflow.users SET password_hash=?, auth_version=auth_version+1, updated_at=? WHERE id=?",
        hash,
        java.sql.Timestamp.from(now),
        id);
    jdbc.update(
        """
        UPDATE shopflow.auth_email_challenges SET status='EXPIRED', otp_verifier=NULL,
          pending_password_hash=NULL, pending_display_name=NULL
        WHERE user_id=? AND purpose IN ('RESET_PASSWORD', 'SET_PASSWORD') AND status NOT IN ('CONSUMED', 'EXPIRED')
        """,
        id);
  }

  public record Account(
      long id,
      String username,
      String displayName,
      String email,
      boolean verified,
      String passwordHash,
      boolean google,
      boolean active,
      long authVersion) {
    public boolean eligible() {
      return active && verified && email != null;
    }
  }
}
