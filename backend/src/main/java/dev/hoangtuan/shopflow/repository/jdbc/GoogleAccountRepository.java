package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.model.access.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoogleAccountRepository {
  private final JdbcTemplate jdbc;

  public GoogleAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void lockSubject(String subject) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        "shopflow.google:" + subject);
  }

  public GoogleAccount lockBySubject(String subject) {
    var rows =
        jdbc.query(
            "SELECT * FROM shopflow.users WHERE google_issuer = 'https://accounts.google.com' AND google_sub = ? FOR UPDATE",
            this::account,
            subject);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public GoogleAccount lockByEmail(String email) {
    var rows =
        jdbc.query("SELECT * FROM shopflow.users WHERE email = ? FOR UPDATE", this::account, email);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public GoogleAccount create(String subject, String email, String name, Instant now) {
    return jdbc.queryForObject(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,google_issuer,google_sub,display_name,role)
        VALUES (?, ?, ?, 'https://accounts.google.com', ?, ?, 'CUSTOMER') RETURNING *
        """,
        this::account,
        "u_" + UUID.randomUUID(),
        email,
        Timestamp.from(now),
        subject,
        name);
  }

  public void link(long id, String subject, Instant now) {
    jdbc.update(
        "UPDATE shopflow.users SET google_issuer = 'https://accounts.google.com', google_sub = ?, updated_at = ? WHERE id = ?",
        subject,
        Timestamp.from(now),
        id);
  }

  private GoogleAccount account(ResultSet rs, int row) throws SQLException {
    return new GoogleAccount(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("display_name"),
        Role.valueOf(rs.getString("role")),
        rs.getBoolean("active"),
        rs.getLong("auth_version"),
        rs.getString("google_sub"));
  }

  public record GoogleAccount(
      long id,
      String username,
      String email,
      String passwordHash,
      String displayName,
      Role role,
      boolean active,
      long authVersion,
      String subject) {}
}
