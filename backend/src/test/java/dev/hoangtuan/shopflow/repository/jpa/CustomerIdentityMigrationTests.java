package dev.hoangtuan.shopflow.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CustomerIdentityMigrationTests {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  private JdbcTemplate jdbc;
  private String schema;

  @BeforeEach
  void baseline() {
    schema = "shopflow";
    flyway(null).clean();
    flyway("6").migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl()
                    + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                    + "currentSchema="
                    + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
  }

  @Test
  void preservesLegacyDataAndValidatesUpgrade() {
    jdbc.update(
        """
        INSERT INTO orders (customer_name, customer_email, receiver_name, receiver_phone,
          address_line, city, payment_method, total_amount, order_ref)
        VALUES ('Legacy', 'legacy@example.com', 'Receiver', '0900000000', 'Address', 'City',
          'COD', 100, 'legacy-order')
        """);
    jdbc.update(
        "INSERT INTO customers (name, email) VALUES ('Legacy customer', 'legacy@example.com')");
    jdbc.update("UPDATE orders SET customer_id = (SELECT id FROM customers)");
    long orderId = jdbc.queryForObject("SELECT id FROM orders", Long.class);
    long productId = jdbc.queryForObject("SELECT MIN(id) FROM products", Long.class);
    jdbc.update("UPDATE inventory_items SET reserved_stock = 1 WHERE product_id = ?", productId);
    jdbc.update(
        "INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, 'Snapshot', 100, 1)",
        orderId,
        productId);
    jdbc.update(
        "INSERT INTO payments (order_id, method, status, amount) VALUES (?, 'COD', 'PENDING', 100)",
        orderId);
    jdbc.update(
        "INSERT INTO stock_movements (product_id, type, quantity, reference_type, reference_id) VALUES (?, 'ORDER_RESERVED', 1, 'ORDER', ?)",
        productId,
        orderId);
    var preservedTables =
        java.util.List.of(
            "customers",
            "products",
            "inventory_items",
            "order_items",
            "payments",
            "stock_movements");
    var snapshots = new java.util.HashMap<String, java.util.List<java.util.Map<String, Object>>>();
    for (String table : preservedTables)
      snapshots.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
    var users = jdbc.queryForList("SELECT * FROM users ORDER BY id");
    var order = jdbc.queryForMap("SELECT * FROM orders");
    flyway(null).migrate();
    flyway(null).validate();
    for (String table : preservedTables)
      assertThat(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"))
          .isEqualTo(snapshots.get(table));
    for (var user : users) {
      assertThat(jdbc.queryForMap("SELECT * FROM users WHERE id = ?", user.get("id")))
          .containsAllEntriesOf(user)
          .containsEntry("auth_version", 0L)
          .containsEntry("email", null);
    }
    assertThat(jdbc.queryForMap("SELECT * FROM orders"))
        .containsAllEntriesOf(order)
        .containsEntry("account_user_id", null);
  }

  @Test
  void migratesFreshPostgresAndH2() {
    flyway(null).clean();
    String fresh = schema;
    var pg =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(fresh)
            .load();
    pg.migrate();
    pg.validate();
    var h2 =
        Flyway.configure()
            .dataSource(
                "jdbc:h2:mem:"
                    + schema
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                "")
            .schemas("shopflow")
            .load();
    h2.migrate();
    h2.validate();
    assertThat(pg.info().current().getVersion().toString()).isEqualTo("9");
    assertThat(h2.info().current().getVersion().toString()).isEqualTo("9");
  }

  @Test
  void rejectsInvalidAccountIdentities() {
    flyway(null).migrate();
    insertUser("local", "local@example.com", "hash", null);
    insertUser("google", "google@example.com", null, "subject");
    assertRejected(() -> insertUser("duplicate", "local@example.com", "hash", null));
    assertRejected(() -> insertUser("mixed", "Local@example.com", "hash", null));
    assertRejected(() -> insertUser("unicode", "é@example.com", "hash", null));
    assertRejected(() -> insertUser("empty", "empty@example.com", null, null));
    assertRejected(() -> insertUser("same-sub", "other@example.com", null, "subject"));
    assertRejected(
        () -> jdbc.update("UPDATE users SET email_verified_at = NULL WHERE username = 'local'"));
    assertRejected(
        () -> jdbc.update("UPDATE users SET google_sub = NULL WHERE username = 'google'"));
    assertRejected(
        () -> jdbc.update("UPDATE users SET auth_version = -1 WHERE username = 'local'"));
  }

  @Test
  void concurrentEmailAndGoogleClaimsHaveOneWinner() throws Exception {
    flyway(null).migrate();
    for (boolean google : new boolean[] {false, true}) {
      var start = new CyclicBarrier(2);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var tasks =
            java.util.stream.IntStream.range(0, 2)
                .mapToObj(
                    i ->
                        executor.submit(
                            () -> {
                              start.await(10, TimeUnit.SECONDS);
                              try {
                                insertUser(
                                    "race-" + google + i,
                                    google ? "google" + i + "@example.com" : "race@example.com",
                                    google ? null : "hash",
                                    google ? "racing-sub" : null);
                                return 1;
                              } catch (DataIntegrityViolationException expected) {
                                return 0;
                              }
                            }))
                .toList();
        assertThat(tasks.get(0).get(15, TimeUnit.SECONDS) + tasks.get(1).get(15, TimeUnit.SECONDS))
            .isEqualTo(1);
      }
    }
  }

  @Test
  void enforcesChallengeTransitionsAndPersistentQuotaShape() {
    flyway(null).migrate();
    UUID generation = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO auth_email_challenges (email, purpose, challenge_id, session_binding,
          otp_verifier, synthetic, status, issued_at, expires_at, resend_at,
          pending_password_hash, pending_display_name)
        VALUES ('signup@example.com', 'REGISTER', ?, ?, ?, FALSE, 'ISSUED',
          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10' MINUTE,
          CURRENT_TIMESTAMP + INTERVAL '60' SECOND, 'hash', 'Customer')
        """,
        generation,
        new byte[32],
        new byte[32]);
    assertRejected(
        () -> jdbc.update("INSERT INTO auth_email_challenges SELECT * FROM auth_email_challenges"));
    assertRejected(
        () ->
            jdbc.update(
                "UPDATE auth_email_challenges SET expires_at = issued_at + INTERVAL '11' MINUTE"));
    assertRejected(() -> jdbc.update("UPDATE auth_email_challenges SET synthetic = TRUE"));
    assertRejected(() -> jdbc.update("UPDATE auth_email_challenges SET failed_attempts = 6"));
    assertRejected(
        () -> jdbc.update("UPDATE auth_email_challenges SET otp_verifier = ?", new byte[31]));
    assertRejected(() -> jdbc.update("UPDATE auth_email_challenges SET status = 'CONSUMED'"));
    jdbc.update("UPDATE auth_email_challenges SET status = 'FAILED', otp_verifier = NULL");
    assertThat(
            jdbc.queryForObject(
                "SELECT pending_password_hash FROM auth_email_challenges", String.class))
        .isEqualTo("hash");
    jdbc.update(
        "UPDATE auth_email_challenges SET status = 'EXPIRED', pending_password_hash = NULL, pending_display_name = NULL");
    assertRejected(() -> jdbc.update("UPDATE auth_email_challenges SET status = 'ISSUED'"));
    jdbc.update(
        """
        INSERT INTO auth_rate_buckets (scope, key_digest, window_started_at, expires_at, used_count)
        VALUES ('EMAIL_SEND', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '15' MINUTE, 1)
        """,
        new byte[32]);
    assertRejected(() -> jdbc.update("UPDATE auth_rate_buckets SET used_count = -1"));
    assertRejected(() -> jdbc.update("UPDATE auth_rate_buckets SET scope = 'UNKNOWN'"));
    assertRejected(() -> jdbc.update("UPDATE auth_rate_buckets SET key_digest = ?", new byte[31]));
    assertRejected(
        () -> jdbc.update("UPDATE auth_rate_buckets SET expires_at = window_started_at"));
  }

  @Test
  void supportsSingleAtomicChallengeConsumption() throws Exception {
    flyway(null).migrate();
    UUID generation = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO auth_email_challenges (email, purpose, challenge_id, user_id,
          session_binding, otp_verifier, synthetic, status, issued_at, expires_at, resend_at)
        SELECT 'reset@example.com', 'RESET_PASSWORD', ?, id, ?, ?, FALSE, 'ISSUED',
          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10' MINUTE,
          CURRENT_TIMESTAMP + INTERVAL '60' SECOND FROM users WHERE username = 'customer'
        """,
        generation,
        new byte[32],
        new byte[32]);
    var start = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            start.await(10, TimeUnit.SECONDS);
                            return jdbc.update(
                                """
            UPDATE auth_email_challenges SET status = 'CONSUMED', consumed_at = CURRENT_TIMESTAMP,
              otp_verifier = NULL
            WHERE challenge_id = ? AND status = 'ISSUED' AND NOT synthetic
              AND expires_at > CURRENT_TIMESTAMP AND failed_attempts < 5
              AND session_binding = ? AND otp_verifier = ?
            """,
                                generation,
                                new byte[32],
                                new byte[32]);
                          }))
              .toList();
      assertThat(tasks.get(0).get(15, TimeUnit.SECONDS) + tasks.get(1).get(15, TimeUnit.SECONDS))
          .isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT otp_verifier FROM auth_email_challenges", byte[].class))
        .isNull();
  }

  @Test
  void preservesOwnerForeignKeyAndDeniesDeletingReferencedUser() {
    flyway(null).migrate();
    jdbc.update(
        """
        INSERT INTO orders (account_user_id, customer_name, receiver_name, receiver_phone,
          address_line, city, payment_method, total_amount)
        SELECT id, 'Customer', 'Receiver', '0900000000', 'Address', 'City', 'COD', 100
        FROM users WHERE username = 'customer'
        """);
    assertRejected(() -> jdbc.update("DELETE FROM users WHERE username = 'customer'"));
    assertRejected(() -> jdbc.update("UPDATE orders SET account_user_id = -1"));
    assertThat(jdbc.queryForObject("SELECT account_user_id FROM orders", Long.class)).isNotNull();
  }

  @Test
  void referenceDdlBuildsIdentityTables() throws Exception {
    try (var connection =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA reference_ddl");
      connection.setSchema("reference_ddl");
      statement.execute(
          java.nio.file.Files.readString(
              java.nio.file.Path.of(System.getProperty("basedir"), "../docs/database-schema.sql")));
      for (String table :
          java.util.List.of("users", "orders", "auth_email_challenges", "auth_rate_buckets")) {
        try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isZero();
        }
      }
    }
  }

  private void assertRejected(Runnable action) {
    assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertUser(String username, String email, String hash, String sub) {
    jdbc.update(
        """
        INSERT INTO users (username, email, email_verified_at, password_hash, display_name,
          role, google_issuer, google_sub)
        VALUES (?, ?, CURRENT_TIMESTAMP, ?, 'Customer', 'CUSTOMER', ?, ?)
        """,
        username,
        email,
        hash,
        sub == null ? null : "https://accounts.google.com",
        sub);
  }

  private Flyway flyway(String target) {
    var config =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .cleanDisabled(false);
    if (target != null) config.target(target);
    return config.load();
  }
}
