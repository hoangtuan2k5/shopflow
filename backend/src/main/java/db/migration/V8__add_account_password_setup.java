package db.migration;

import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__add_account_password_setup extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws SQLException {
    var connection = context.getConnection();
    String purposeConstraint = null;
    try (var query = connection.createStatement();
        var rows =
            query.executeQuery(
                """
        SELECT t.constraint_name, c.check_clause
        FROM information_schema.table_constraints t
        JOIN information_schema.check_constraints c
          ON t.constraint_catalog=c.constraint_catalog AND t.constraint_schema=c.constraint_schema
          AND t.constraint_name=c.constraint_name
        WHERE LOWER(t.table_schema)='shopflow' AND LOWER(t.table_name)='auth_email_challenges'
          AND t.constraint_type='CHECK'
        """)) {
      while (rows.next()) {
        String clause = rows.getString(2).toLowerCase(java.util.Locale.ROOT);
        // V7 left this name to the database; PostgreSQL and H2 generate different names.
        if (clause.contains("purpose")
            && clause.contains("'register'")
            && clause.contains("'reset_password'")
            && !clause.contains("user_id")
            && !clause.contains("synthetic")
            && !clause.contains("status")) {
          if (purposeConstraint != null)
            throw new SQLException("Multiple purpose constraints found");
          purposeConstraint = rows.getString(1);
        }
      }
    }
    if (purposeConstraint == null) throw new SQLException("V7 purpose constraint not found");
    try (var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE auth_email_challenges ADD COLUMN issued_auth_version BIGINT");
      statement.execute(
          "ALTER TABLE auth_email_challenges DROP CONSTRAINT \""
              + purposeConstraint.replace("\"", "\"\"")
              + "\"");
      for (String command :
"""
ALTER TABLE auth_email_challenges ADD CONSTRAINT auth_email_challenges_purpose_check
    CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD', 'SET_PASSWORD'));
ALTER TABLE auth_email_challenges DROP CONSTRAINT chk_challenges_target;
ALTER TABLE auth_email_challenges ADD CONSTRAINT chk_challenges_target CHECK (
    (purpose = 'REGISTER' AND user_id IS NULL AND issued_auth_version IS NULL) OR
    (purpose = 'RESET_PASSWORD' AND (synthetic OR user_id IS NOT NULL) AND issued_auth_version IS NULL) OR
    (purpose = 'SET_PASSWORD' AND NOT synthetic AND user_id IS NOT NULL
      AND issued_auth_version IS NOT NULL AND issued_auth_version >= 0));
ALTER TABLE auth_email_challenges DROP CONSTRAINT chk_challenges_payload;
ALTER TABLE auth_email_challenges ADD CONSTRAINT chk_challenges_payload CHECK (
    (NOT synthetic AND purpose = 'REGISTER' AND status IN ('PENDING_SEND', 'ISSUED', 'FAILED')
      AND pending_password_hash IS NOT NULL AND TRIM(pending_password_hash) <> ''
      AND pending_display_name IS NOT NULL AND TRIM(pending_display_name) <> '') OR
    ((synthetic OR purpose IN ('RESET_PASSWORD', 'SET_PASSWORD') OR status IN ('CONSUMED', 'EXPIRED'))
      AND pending_password_hash IS NULL AND pending_display_name IS NULL));
          """
              .split(";")) {
        if (!command.isBlank()) statement.execute(command);
      }
    }
  }
}
