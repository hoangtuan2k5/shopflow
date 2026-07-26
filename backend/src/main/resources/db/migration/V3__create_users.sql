-- =====================================================================
-- Flyway migration V3 — User accounts for authentication (SF-72)
-- DBMS  : PostgreSQL 14+
-- Schema: shopflow (auto-managed by flyway.schemas in application.yaml)
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. USERS
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL
                   CHECK (role IN ('CUSTOMER', 'WAREHOUSE', 'SHOP_OWNER')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_username UNIQUE (username)
);


-- ---------------------------------------------------------------------
-- 2. DEMO ACCOUNTS
--
-- Seeded in the main migration (not db/dev) because the production VPS is
-- the demo environment: without these there is no account to sign in with
-- after deployment.
--
-- SECURITY: these passwords live in the repository and are therefore public
-- knowledge. They may only ever guard demo data. Rotate or deactivate them
-- before this system holds anything real.
--
--   customer  / Customer@2026
--   warehouse / Warehouse@2026
--   owner     / Owner@2026
--
-- Hashes are BCrypt, cost 10.
-- ---------------------------------------------------------------------
INSERT INTO users (username, password_hash, display_name, role) VALUES
    ('customer',
     '$2a$10$ntVyHWRw0Rou2uyqTOdnkeSy9YfDxBf5rZm7SgSdAIW41WIoPRTf2',
     'Khách hàng demo',
     'CUSTOMER'),
    ('warehouse',
     '$2a$10$oV7R7Trm6VumlmwSOKNlk.AyrNMezI0jFS5BGk7XsSEJZ8sKdOGt2',
     'Nhân viên kho demo',
     'WAREHOUSE'),
    ('owner',
     '$2a$10$Vz/9XbGdm7RZL9/kbG3UP.3dUuPw1.6HJUUiC/4UT/L1SbNtToGZy',
     'Chủ shop demo',
     'SHOP_OWNER');
