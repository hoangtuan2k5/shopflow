package dev.hoangtuan.shopflow.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CustomerAuthRolloutTests {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("rollout_source");

  @Test
  void restoresBaselineAndUpgradedBackupsWithoutChangingLegacySalesOrCredentials()
      throws Exception {
    flyway("rollout_source", "6").migrate();
    JdbcTemplate source = jdbc("rollout_source");
    long product = source.queryForObject("SELECT MIN(id) FROM shopflow.products", Long.class);
    long order =
        source.queryForObject(
            """
        INSERT INTO shopflow.orders (customer_name,customer_email,receiver_name,receiver_phone,
          address_line,city,payment_method,total_amount)
        VALUES ('Rehearsal legacy','legacy@example.invalid','Receiver','0900000000',
          'Test address','Test city','CARD',100000) RETURNING id
        """,
            Long.class);
    source.update(
        "INSERT INTO shopflow.order_items (order_id,product_id,product_name,unit_price,quantity) VALUES (?,?,'Historical snapshot',100000,1)",
        order,
        product);
    source.update(
        "UPDATE shopflow.inventory_items SET reserved_stock = 1 WHERE product_id = ?", product);
    source.update(
        "INSERT INTO shopflow.stock_movements (product_id,type,quantity,reference_type,reference_id) VALUES (?,'ORDER_RESERVED',1,'ORDER',?)",
        product,
        order);
    Map<String, String> legacy = snapshot(source, true);

    backupAndRestore("rollout_source", "restored_v6", "/tmp/baseline.dump");
    assertThat(snapshot(jdbc("restored_v6"), true)).isEqualTo(legacy);
    assertThat(flyway("restored_v6", "6").info().current().getVersion().getVersion())
        .isEqualTo("6");

    long started = System.nanoTime();
    flyway("rollout_source", null).migrate();
    long migrationMillis = (System.nanoTime() - started) / 1_000_000;
    flyway("rollout_source", null).validate();
    flyway("restored_v6", null).migrate();
    flyway("restored_v6", null).validate();
    assertThat(snapshot(source, true)).isEqualTo(legacy);
    assertThat(snapshot(jdbc("restored_v6"), true)).isEqualTo(legacy);
    assertThat(
            source.queryForObject(
                "SELECT account_user_id FROM shopflow.orders WHERE id = ?", Long.class, order))
        .isNull();
    assertThat(
            source.queryForObject(
                "SELECT reserved_stock FROM shopflow.inventory_items WHERE product_id = ?",
                Integer.class,
                product))
        .isEqualTo(1);

    // An old producer's insert still fits the additive schema; do not keep its test order.
    try (var connection =
        DriverManager.getConnection(
            url("rollout_source"), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  """
              INSERT INTO shopflow.orders (customer_name,receiver_name,receiver_phone,address_line,city,payment_method,total_amount)
              VALUES ('Old producer','Receiver','0900000000','Address','City','CARD',100000)
              RETURNING account_user_id
              """)) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getObject(1)).isNull();
      } finally {
        connection.rollback();
      }
    }
    assertThat(snapshot(source, true)).isEqualTo(legacy);

    long local =
        source.queryForObject(
            """
        INSERT INTO shopflow.users (username,email,email_verified_at,password_hash,display_name,role)
        SELECT 'rollout-local','local@example.invalid',CURRENT_TIMESTAMP,password_hash,'Local','CUSTOMER'
        FROM shopflow.users WHERE username = 'customer' RETURNING id
        """,
            Long.class);
    source.update(
        """
        INSERT INTO shopflow.users (username,email,email_verified_at,google_issuer,google_sub,display_name,role)
        VALUES ('rollout-google','google@example.invalid',CURRENT_TIMESTAMP,'https://accounts.google.com','rollout-subject','Google','CUSTOMER')
        """);
    source.update(
        """
        INSERT INTO shopflow.orders (customer_name,receiver_name,receiver_phone,address_line,city,payment_method,total_amount,account_user_id)
        VALUES ('Owned','Receiver','0900000000','Address','City','CARD',100000,?)
        """,
        local);
    Map<String, String> upgraded = snapshot(source, false);
    backupAndRestore("rollout_source", "restored_v7", "/tmp/upgraded.dump");
    JdbcTemplate restored = jdbc("restored_v7");
    flyway("restored_v7", null).validate();
    assertThat(snapshot(restored, false)).isEqualTo(upgraded);
    assertThat(
            restored.queryForObject(
                "SELECT COUNT(*) FROM shopflow.users WHERE google_sub IS NOT NULL AND password_hash IS NULL",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            restored.queryForObject(
                "SELECT COUNT(*) FROM shopflow.orders WHERE account_user_id = ?",
                Integer.class,
                local))
        .isEqualTo(1);
    assertThat(
            restored.queryForObject(
                "SELECT COUNT(*) FROM shopflow.orders WHERE account_user_id IS NULL",
                Integer.class))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                restored.update(
                    "UPDATE shopflow.orders SET account_user_id = -1 WHERE account_user_id = ?",
                    local))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(snapshot(restored, false)).isEqualTo(upgraded);
    assertThat(restored.queryForObject("SELECT last_value FROM shopflow.orders_id_seq", Long.class))
        .isEqualTo(
            source.queryForObject("SELECT last_value FROM shopflow.orders_id_seq", Long.class));
    System.out.println(
        "SF-149 disposable PostgreSQL17: V7 migration "
            + migrationMillis
            + " ms; V6/V7 pg_dump+pg_restore and all application-table digests matched");
  }

  @Test
  void upgradesV7AndRestoresAccountDataOnPostgres17() throws Exception {
    command("createdb", "-U", POSTGRES.getUsername(), "account_v7");
    flyway("account_v7", "7").migrate();
    JdbcTemplate db = jdbc("account_v7");
    long google =
        db.queryForObject(
            """
        INSERT INTO shopflow.users (username,email,email_verified_at,google_issuer,google_sub,display_name,role)
        VALUES ('account-google','google@example.invalid',now(),'https://accounts.google.com','account-sub','Google','CUSTOMER') RETURNING id
        """,
            Long.class);
    db.update(
        """
        INSERT INTO shopflow.auth_email_challenges
        (email,purpose,challenge_id,session_binding,otp_verifier,synthetic,status,issued_at,expires_at,resend_at,pending_password_hash,pending_display_name)
        VALUES ('pending@example.invalid','REGISTER',gen_random_uuid(),decode(repeat('01',32),'hex'),decode(repeat('02',32),'hex'),false,'ISSUED',now(),now()+interval '10 minute',now()+interval '60 second','test-only-hash','Pending')
        """);
    Map<String, String> before = snapshot(db, false);
    backupAndRestore("account_v7", "account_restore_v7", "/tmp/account-v7.dump");
    flyway("account_v7", null).migrate();
    flyway("account_v7", null).validate();
    flyway("account_restore_v7", null).migrate();
    flyway("account_restore_v7", null).validate();
    var after = snapshot(db, false);
    before.forEach(
        (table, digest) ->
            assertThat(after.get(table)).as("V7 rows in " + table).isEqualTo(digest));
    before.forEach(
        (table, digest) -> {
          try {
            assertThat(snapshot(jdbc("account_restore_v7"), false).get(table)).isEqualTo(digest);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM shopflow.account_delivery_details", Integer.class))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT issued_auth_version FROM shopflow.auth_email_challenges WHERE email='pending@example.invalid'",
                Long.class))
        .isNull();
    db.update(
        "INSERT INTO shopflow.account_delivery_details VALUES (?,'Receiver','0900000000','Private address',NULL,'Hanoi',now(),now())",
        google);
    db.update(
        """
        INSERT INTO shopflow.auth_email_challenges
        (email,purpose,challenge_id,user_id,session_binding,otp_verifier,synthetic,status,issued_at,expires_at,resend_at,issued_auth_version)
        VALUES ('google@example.invalid','SET_PASSWORD',gen_random_uuid(),?,decode(repeat('01',32),'hex'),decode(repeat('02',32),'hex'),false,'ISSUED',now(),now()+interval '10 minute',now()+interval '60 second',0)
        """,
        google);
    Map<String, String> upgraded = snapshot(db, false);
    backupAndRestore("account_v7", "account_restore_v9", "/tmp/account-v9.dump");
    flyway("account_restore_v9", null).validate();
    assertThat(snapshot(jdbc("account_restore_v9"), false)).isEqualTo(upgraded);
    assertThat(
            jdbc("account_restore_v9")
                .queryForObject(
                    "SELECT issued_auth_version FROM shopflow.auth_email_challenges WHERE purpose='SET_PASSWORD'",
                    Long.class))
        .isZero();
    System.out.println(
        "SF-156 PostgreSQL17: V7/V9 backup, restore, migrations and application row digests passed");
  }

  private void backupAndRestore(String source, String destination, String path) throws Exception {
    command("pg_dump", "-U", POSTGRES.getUsername(), "-d", source, "-Fc", "-f", path);
    command("createdb", "-U", POSTGRES.getUsername(), destination);
    command(
        "pg_restore",
        "-U",
        POSTGRES.getUsername(),
        "--exit-on-error",
        "--no-owner",
        "-d",
        destination,
        path);
  }

  private void command(String... args) throws Exception {
    assertThat(POSTGRES.execInContainer(args).getExitCode())
        .as("disposable database command")
        .isZero();
  }

  private String url(String database) {
    return POSTGRES.getJdbcUrl().replace("/rollout_source", "/" + database);
  }

  private JdbcTemplate jdbc(String database) {
    return new JdbcTemplate(
        new DriverManagerDataSource(url(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
  }

  private Flyway flyway(String database, String target) {
    var config =
        Flyway.configure()
            .dataSource(url(database), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("shopflow")
            .defaultSchema("shopflow");
    if (target != null) config.target(target);
    return config.load();
  }

  private Map<String, String> snapshot(JdbcTemplate jdbc, boolean legacy) throws Exception {
    Map<String, String> digests = new LinkedHashMap<>();
    for (String table :
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='shopflow' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history' ORDER BY table_name",
            String.class)) {
      assertThat(table).matches("[a-z][a-z0-9_]*");
      if (legacy && (table.startsWith("auth_") || table.equals("account_delivery_details")))
        continue;
      String row = "to_jsonb(t)";
      if (legacy && table.equals("users"))
        row += " - ARRAY['email','email_verified_at','google_issuer','google_sub','auth_version']";
      if (table.equals("auth_email_challenges")) row += " - 'issued_auth_version'";
      if (legacy && table.equals("orders")) row += " - 'account_user_id'";
      String data =
          jdbc.queryForObject(
              "SELECT COALESCE(jsonb_agg(("
                  + row
                  + ") ORDER BY ("
                  + row
                  + ")::text)::text,'[]') FROM shopflow."
                  + table
                  + " t",
              String.class);
      digests.put(
          table,
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(data.getBytes(StandardCharsets.UTF_8))));
    }
    return digests;
  }
}
