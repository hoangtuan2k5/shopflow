package dev.hoangtuan.shopflow.repository.jdbc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmailChallengeRepository {
  private final JdbcTemplate jdbc;

  public EmailChallengeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Bucket> lockRates(List<Rate> rates, Instant now) {
    List<Rate> sorted = new ArrayList<>(rates);
    sorted.sort(
        Comparator.comparing(
            rate -> rate.scope() + ":" + HexFormat.of().formatHex(digest(rate.key()))));
    Map<String, Bucket> buckets = new LinkedHashMap<>();
    for (Rate rate : sorted) {
      byte[] key = digest(rate.key());
      jdbc.update(
          """
          INSERT INTO shopflow.auth_rate_buckets (scope, key_digest, window_started_at, expires_at, used_count)
          VALUES (?, ?, ?, ?, 0) ON CONFLICT (scope, key_digest) DO NOTHING
          """,
          rate.scope(),
          key,
          Timestamp.from(now),
          Timestamp.from(rate.expiresAt()));
      Bucket bucket =
          jdbc.queryForObject(
              """
          SELECT used_count, expires_at FROM shopflow.auth_rate_buckets
          WHERE scope = ? AND key_digest = ? FOR UPDATE
          """,
              (rs, n) ->
                  new Bucket(
                      rate.scope(),
                      key,
                      rs.getInt("used_count"),
                      rs.getTimestamp("expires_at").toInstant(),
                      rate.limit()),
              rate.scope(),
              key);
      if (!bucket.expiresAt().isAfter(now)) {
        jdbc.update(
            """
            UPDATE shopflow.auth_rate_buckets SET window_started_at = ?, expires_at = ?, used_count = 0
            WHERE scope = ? AND key_digest = ?
            """,
            Timestamp.from(now),
            Timestamp.from(rate.expiresAt()),
            rate.scope(),
            key);
        bucket = new Bucket(rate.scope(), key, 0, rate.expiresAt(), rate.limit());
      }
      buckets.put(rate.scope(), bucket);
    }
    return buckets;
  }

  public void increment(Bucket bucket) {
    jdbc.update(
        "UPDATE shopflow.auth_rate_buckets SET used_count = used_count + 1 WHERE scope = ? AND key_digest = ?",
        bucket.scope(),
        bucket.digest());
  }

  public void lockEmail(String email) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        "shopflow.email:" + email);
  }

  public Account lockAccount(String email) {
    var accounts =
        jdbc.query(
            """
        SELECT id, active, password_hash IS NOT NULL AS local_credential,
          email_verified_at IS NOT NULL AS verified
        FROM shopflow.users WHERE email = ? FOR UPDATE
        """,
            (rs, n) ->
                new Account(
                    rs.getLong("id"),
                    rs.getBoolean("active"),
                    rs.getBoolean("local_credential"),
                    rs.getBoolean("verified")),
            email);
    return accounts.isEmpty() ? null : accounts.getFirst();
  }

  public Challenge find(UUID generation) {
    var rows =
        jdbc.query(
            "SELECT * FROM shopflow.auth_email_challenges WHERE challenge_id = ?",
            this::challenge,
            generation);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Challenge lockChallenge(String email, String purpose) {
    var rows =
        jdbc.query(
            "SELECT * FROM shopflow.auth_email_challenges WHERE email = ? AND purpose = ? FOR UPDATE",
            this::challenge,
            email,
            purpose);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void replace(
      String email,
      String purpose,
      Long userId,
      UUID generation,
      byte[] binding,
      byte[] verifier,
      boolean synthetic,
      String passwordHash,
      String name,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO shopflow.auth_email_challenges (email, purpose, challenge_id, user_id,
          session_binding, otp_verifier, synthetic, status, issued_at, expires_at, resend_at,
          failed_attempts, pending_password_hash, pending_display_name, consumed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, NULL)
        ON CONFLICT (email, purpose) DO UPDATE SET challenge_id = EXCLUDED.challenge_id,
          user_id = EXCLUDED.user_id, session_binding = EXCLUDED.session_binding, otp_verifier = EXCLUDED.otp_verifier,
          synthetic = EXCLUDED.synthetic, status = EXCLUDED.status, issued_at = EXCLUDED.issued_at,
          expires_at = EXCLUDED.expires_at, resend_at = EXCLUDED.resend_at, failed_attempts = 0,
          pending_password_hash = EXCLUDED.pending_password_hash,
          pending_display_name = EXCLUDED.pending_display_name, consumed_at = NULL
        """,
        email,
        purpose,
        generation,
        synthetic ? null : userId,
        binding,
        synthetic ? null : verifier,
        synthetic,
        synthetic ? "ISSUED" : "PENDING_SEND",
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(600)),
        Timestamp.from(now.plusSeconds(60)),
        synthetic ? null : passwordHash,
        synthetic ? null : name);
  }

  public void delivery(UUID generation, boolean success) {
    jdbc.update(
        """
        UPDATE shopflow.auth_email_challenges SET status = ?,
          otp_verifier = CASE WHEN ? THEN otp_verifier ELSE NULL END
        WHERE challenge_id = ? AND status = 'PENDING_SEND'
        """,
        success ? "ISSUED" : "FAILED",
        success,
        generation);
  }

  public void expire(UUID generation) {
    jdbc.update(
        """
        UPDATE shopflow.auth_email_challenges SET status = 'EXPIRED', otp_verifier = NULL,
          pending_password_hash = NULL, pending_display_name = NULL
        WHERE challenge_id = ? AND status NOT IN ('CONSUMED', 'EXPIRED')
        """,
        generation);
  }

  public void failedAttempt(UUID generation) {
    jdbc.update(
        "UPDATE shopflow.auth_email_challenges SET failed_attempts = failed_attempts + 1 WHERE challenge_id = ?",
        generation);
  }

  public void activate(Challenge challenge, Instant now) {
    jdbc.update(
        """
        INSERT INTO shopflow.users (username, email, email_verified_at, password_hash, display_name, role)
        VALUES (?, ?, ?, ?, ?, 'CUSTOMER')
        """,
        "u_" + UUID.randomUUID(),
        challenge.email(),
        Timestamp.from(now),
        challenge.passwordHash(),
        challenge.displayName());
    consume(challenge.id(), now);
  }

  public void resetPassword(Challenge challenge, String hash, Instant now) {
    jdbc.update(
        """
        UPDATE shopflow.users SET password_hash = ?, auth_version = auth_version + 1, updated_at = ?
        WHERE id = ?
        """,
        hash,
        Timestamp.from(now),
        challenge.userId());
    consume(challenge.id(), now);
    jdbc.update(
        """
        UPDATE shopflow.auth_email_challenges SET status = 'EXPIRED', otp_verifier = NULL,
          pending_password_hash = NULL, pending_display_name = NULL
        WHERE purpose = 'RESET_PASSWORD' AND user_id = ? AND challenge_id <> ?
          AND status NOT IN ('CONSUMED', 'EXPIRED')
        """,
        challenge.userId(),
        challenge.id());
  }

  private void consume(UUID id, Instant now) {
    jdbc.update(
        """
        UPDATE shopflow.auth_email_challenges SET status = 'CONSUMED', consumed_at = ?,
          otp_verifier = NULL, pending_password_hash = NULL, pending_display_name = NULL
        WHERE challenge_id = ?
        """,
        Timestamp.from(now),
        id);
  }

  public void cleanup(Instant now) {
    Timestamp cutoff = Timestamp.from(now.minusSeconds(86400));
    jdbc.update(
        """
        DELETE FROM shopflow.auth_email_challenges WHERE challenge_id IN (
          SELECT challenge_id FROM shopflow.auth_email_challenges WHERE expires_at < ?
          ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED)
        """,
        cutoff);
    jdbc.update(
        """
        DELETE FROM shopflow.auth_rate_buckets WHERE (scope, key_digest) IN (
          SELECT scope, key_digest FROM shopflow.auth_rate_buckets WHERE expires_at < ?
          ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED)
        """,
        cutoff);
  }

  private Challenge challenge(ResultSet rs, int n) throws SQLException {
    return new Challenge(
        rs.getObject("challenge_id", UUID.class),
        rs.getString("email"),
        rs.getString("purpose"),
        rs.getObject("user_id", Long.class),
        rs.getBytes("session_binding"),
        rs.getBytes("otp_verifier"),
        rs.getBoolean("synthetic"),
        rs.getString("status"),
        rs.getTimestamp("issued_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("resend_at").toInstant(),
        rs.getInt("failed_attempts"),
        rs.getString("pending_password_hash"),
        rs.getString("pending_display_name"));
  }

  private byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public record Account(long id, boolean active, boolean localCredential, boolean verified) {
    public boolean recoverable() {
      return active && localCredential && verified;
    }
  }

  public record Rate(String scope, String key, int limit, Instant expiresAt) {}

  public record Bucket(String scope, byte[] digest, int count, Instant expiresAt, int limit) {}

  public record Challenge(
      UUID id,
      String email,
      String purpose,
      Long userId,
      byte[] binding,
      byte[] verifier,
      boolean synthetic,
      String status,
      Instant issuedAt,
      Instant expiresAt,
      Instant resendAt,
      int failedAttempts,
      String passwordHash,
      String displayName) {}
}
