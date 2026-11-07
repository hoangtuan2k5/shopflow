-- SF-155: existing challenge generations retain their verifier and expiry.
ALTER TABLE auth_email_challenges ADD COLUMN issued_auth_version BIGINT;
ALTER TABLE auth_email_challenges DROP CONSTRAINT auth_email_challenges_purpose_check;
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
