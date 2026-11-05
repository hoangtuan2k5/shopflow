-- SF-142: additive identity foundation; legacy accounts and orders keep their data.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN google_issuer VARCHAR(255);
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255);
ALTER TABLE users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT uq_users_google_identity UNIQUE (google_issuer, google_sub);
ALTER TABLE users ADD CONSTRAINT chk_users_email CHECK (
    (email IS NULL AND email_verified_at IS NULL) OR
    (email IS NOT NULL AND email_verified_at IS NOT NULL
     AND email = LOWER(TRIM(email)) AND email <> ''
     AND OCTET_LENGTH(email) = CHAR_LENGTH(email))
);
ALTER TABLE users ADD CONSTRAINT chk_users_google_identity CHECK (
    (google_issuer IS NULL AND google_sub IS NULL) OR
    (google_issuer IS NOT NULL AND google_issuer = 'https://accounts.google.com'
     AND google_sub IS NOT NULL AND TRIM(google_sub) <> '' AND email IS NOT NULL)
);
ALTER TABLE users ADD CONSTRAINT chk_users_credential CHECK (
    (password_hash IS NOT NULL AND TRIM(password_hash) <> '') OR google_sub IS NOT NULL
);
ALTER TABLE users ADD CONSTRAINT chk_users_auth_version CHECK (auth_version >= 0);

CREATE TABLE auth_email_challenges (
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD')),
    challenge_id UUID NOT NULL UNIQUE,
    user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    session_binding BYTEA NOT NULL CHECK (OCTET_LENGTH(session_binding) = 32),
    otp_verifier BYTEA CHECK (otp_verifier IS NULL OR OCTET_LENGTH(otp_verifier) = 32),
    synthetic BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_SEND', 'ISSUED', 'FAILED', 'CONSUMED', 'EXPIRED')),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resend_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    pending_password_hash VARCHAR(255),
    pending_display_name VARCHAR(255),
    consumed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (email, purpose),
    CONSTRAINT chk_challenges_email CHECK (
        email = LOWER(TRIM(email)) AND email <> '' AND OCTET_LENGTH(email) = CHAR_LENGTH(email)),
    CONSTRAINT chk_challenges_times CHECK (
        expires_at = issued_at + INTERVAL '10' MINUTE AND resend_at = issued_at + INTERVAL '60' SECOND),
    CONSTRAINT chk_challenges_consumed CHECK (
        (status = 'CONSUMED' AND consumed_at IS NOT NULL AND consumed_at >= issued_at AND consumed_at < expires_at)
        OR (status <> 'CONSUMED' AND consumed_at IS NULL)),
    CONSTRAINT chk_challenges_target CHECK (
        (purpose = 'REGISTER' AND user_id IS NULL) OR
        (purpose = 'RESET_PASSWORD' AND (synthetic OR user_id IS NOT NULL))),
    CONSTRAINT chk_challenges_verifier CHECK (
        (synthetic AND otp_verifier IS NULL) OR
        (NOT synthetic AND ((status IN ('PENDING_SEND', 'ISSUED') AND otp_verifier IS NOT NULL)
          OR (status IN ('FAILED', 'CONSUMED', 'EXPIRED') AND otp_verifier IS NULL)))),
    CONSTRAINT chk_challenges_payload CHECK (
        (NOT synthetic AND purpose = 'REGISTER' AND status IN ('PENDING_SEND', 'ISSUED', 'FAILED')
          AND pending_password_hash IS NOT NULL AND TRIM(pending_password_hash) <> ''
          AND pending_display_name IS NOT NULL AND TRIM(pending_display_name) <> '') OR
        ((synthetic OR purpose = 'RESET_PASSWORD' OR status IN ('CONSUMED', 'EXPIRED'))
          AND pending_password_hash IS NULL AND pending_display_name IS NULL)),
    CONSTRAINT chk_challenges_synthetic CHECK (NOT synthetic OR (user_id IS NULL AND status <> 'CONSUMED'))
);
CREATE INDEX idx_auth_email_challenges_expires_at ON auth_email_challenges (expires_at);

CREATE TABLE auth_rate_buckets (
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('EMAIL_SEND', 'EMAIL_VERIFY_FAILURE', 'IP_SEND', 'IP_VERIFY', 'MAIL_DAILY')),
    key_digest BYTEA NOT NULL CHECK (OCTET_LENGTH(key_digest) = 32),
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_count INT NOT NULL CHECK (used_count >= 0),
    PRIMARY KEY (scope, key_digest),
    CONSTRAINT chk_rate_buckets_window CHECK (expires_at > window_started_at)
);
CREATE INDEX idx_auth_rate_buckets_expires_at ON auth_rate_buckets (expires_at);

ALTER TABLE orders ADD COLUMN account_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT;
CREATE INDEX idx_orders_account_user_id ON orders (account_user_id);
