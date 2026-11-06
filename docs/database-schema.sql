-- =====================================================================
-- ShopFlow — Database Schema (MVP)
-- Target DBMS  : PostgreSQL 14+
-- Schema       : shopflow
-- Reference DDL through V7; deploy with Flyway, never run over an existing DB.
-- Normal form  : 3NF + snapshot pattern (orders, order_items)
-- For dbdiagram.io:
--   1. Mở https://dbdiagram.io
--   2. Click "Import" (góc trên-trái) → "Import from PostgreSQL"
--   3. Paste toàn bộ file này vào → click "Submit"
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. PRODUCTS
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id                   BIGSERIAL    PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    image_key            VARCHAR(255),
    price                NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    low_stock_threshold  INT          CHECK (low_stock_threshold >= 0),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_active ON products (active);


-- ---------------------------------------------------------------------
-- 2. CUSTOMERS  (optional - orders đã có snapshot fields)
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    email       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_email ON customers (email);
CREATE INDEX idx_customers_phone ON customers (phone);


-- ---------------------------------------------------------------------
-- 3. INVENTORY_ITEMS  (1-1 với products)
-- ---------------------------------------------------------------------
CREATE TABLE inventory_items (
    id              BIGSERIAL    PRIMARY KEY,
    product_id      BIGINT       NOT NULL UNIQUE,
    on_hand_stock   INT          NOT NULL DEFAULT 0 CHECK (on_hand_stock >= 0),
    reserved_stock  INT          NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT chk_reserved_le_onhand
        CHECK (reserved_stock <= on_hand_stock)
);

-- available_stock KHÔNG lưu DB — tính runtime: on_hand_stock - reserved_stock


-- ---------------------------------------------------------------------
-- 4. ORDERS  (chứa customer snapshot + shipping address inline)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                BIGSERIAL    PRIMARY KEY,
    order_ref         VARCHAR(36) NOT NULL DEFAULT CAST(gen_random_uuid() AS VARCHAR(36)),
    customer_id       BIGINT,
    customer_name     VARCHAR(255) NOT NULL,
    customer_phone    VARCHAR(20),
    customer_email    VARCHAR(255),
    receiver_name     VARCHAR(255) NOT NULL,
    receiver_phone    VARCHAR(20)  NOT NULL,
    address_line      VARCHAR(500) NOT NULL,
    district          VARCHAR(100),
    city              VARCHAR(100) NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PAYMENT',
    delivery_status   VARCHAR(30)  NOT NULL DEFAULT 'NONE',
    payment_method    VARCHAR(20)  NOT NULL,
    total_amount      NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_status
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'CANCELLED')),
    CONSTRAINT chk_orders_delivery_status
        CHECK (delivery_status IN ('NONE', 'PREPARING', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT chk_orders_payment_method
        CHECK (payment_method IN ('CARD', 'COD'))
);

CREATE UNIQUE INDEX uq_orders_order_ref ON orders (order_ref);

CREATE INDEX idx_orders_customer_id     ON orders (customer_id);
CREATE INDEX idx_orders_status          ON orders (status);
CREATE INDEX idx_orders_delivery_status ON orders (delivery_status);
CREATE INDEX idx_orders_created_at      ON orders (created_at DESC);


-- ---------------------------------------------------------------------
-- 5. ORDER_ITEMS  (snapshot product_name + unit_price)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id            BIGSERIAL    PRIMARY KEY,
    order_id      BIGINT       NOT NULL,
    product_id    BIGINT       NOT NULL,
    product_name  VARCHAR(255) NOT NULL,
    unit_price    NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity      INT          NOT NULL CHECK (quantity > 0),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)   REFERENCES orders(id)   ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX idx_order_items_order_id   ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);


-- ---------------------------------------------------------------------
-- 6. PAYMENTS  (1-N vật lý; MVP tối đa một attempt, retry để mở rộng)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id             BIGSERIAL    PRIMARY KEY,
    order_id       BIGINT       NOT NULL,
    method         VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    amount         NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    paid_at        TIMESTAMPTZ,
    failed_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_payments_method
        CHECK (method IN ('CARD', 'COD')),
    CONSTRAINT chk_payments_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED'))
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_status   ON payments (status);


-- ---------------------------------------------------------------------
-- 7. STOCK_MOVEMENTS  (audit trail trung tâm cho mọi thay đổi stock)
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
    id              BIGSERIAL    PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    quantity        INT          NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    BIGINT,
    note            VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),

    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT chk_stock_movements_type
        CHECK (type IN (
            'ORDER_RESERVED',
            'PAYMENT_FAILED_RELEASE',
            'STOCK_RECEIVED',
            'MANUAL_ADJUSTMENT',
            'RETURN_RESTOCK',
            'DELIVERY_COMPLETED'
        )),
    CONSTRAINT chk_stock_movements_reference_type
        CHECK (reference_type IS NULL OR reference_type IN (
            'ORDER', 'RECEIVING', 'RETURN', 'ADJUSTMENT'
        ))
);

CREATE INDEX idx_stock_movements_product_created
    ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_stock_movements_reference
    ON stock_movements (reference_type, reference_id);


-- ---------------------------------------------------------------------
-- 8. RECEIVING_RECORDS  (nhập hàng từ supplier)
-- ---------------------------------------------------------------------
CREATE TABLE receiving_records (
    id             BIGSERIAL    PRIMARY KEY,
    product_id     BIGINT       NOT NULL,
    quantity       INT          NOT NULL CHECK (quantity > 0),
    supplier_name  VARCHAR(255),
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),

    CONSTRAINT fk_receiving_records_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX idx_receiving_records_product_id ON receiving_records (product_id);
CREATE INDEX idx_receiving_records_created_at ON receiving_records (created_at DESC);


-- ---------------------------------------------------------------------
-- 9. RETURN_REQUESTS
-- ---------------------------------------------------------------------
CREATE TABLE return_requests (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    reason       VARCHAR(500),
    restockable  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_return_requests_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT chk_return_requests_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'RESTOCKED', 'REJECTED'))
);

CREATE INDEX idx_return_requests_order_id ON return_requests (order_id);
CREATE INDEX idx_return_requests_status   ON return_requests (status);


-- ---------------------------------------------------------------------
-- 10. RETURN_REQUEST_ITEMS  (partial return theo từng order_item)
-- ---------------------------------------------------------------------
CREATE TABLE return_request_items (
    id                 BIGSERIAL  PRIMARY KEY,
    return_request_id  BIGINT     NOT NULL,
    order_item_id      BIGINT     NOT NULL,
    quantity           INT        NOT NULL CHECK (quantity > 0),

    CONSTRAINT fk_return_items_request
        FOREIGN KEY (return_request_id) REFERENCES return_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_return_items_order_item
        FOREIGN KEY (order_item_id)     REFERENCES order_items(id)     ON DELETE RESTRICT
);

CREATE INDEX idx_return_items_request_id    ON return_request_items (return_request_id);
CREATE INDEX idx_return_items_order_item_id ON return_request_items (order_item_id);


-- ---------------------------------------------------------------------
-- 11. DELIVERY_STATUS_HISTORY  (audit trail cho FR-04.4)
-- ---------------------------------------------------------------------
CREATE TABLE delivery_status_history (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     BIGINT       NOT NULL,
    from_status  VARCHAR(30),
    to_status    VARCHAR(30)  NOT NULL,
    changed_by   VARCHAR(100),
    changed_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note         VARCHAR(500),

    CONSTRAINT fk_delivery_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_delivery_history_from_status
        CHECK (from_status IS NULL OR from_status IN ('NONE', 'PREPARING', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT chk_delivery_history_to_status
        CHECK (to_status IN ('NONE', 'PREPARING', 'SHIPPED', 'DELIVERED'))
);

CREATE INDEX idx_delivery_history_order_changed
    ON delivery_status_history (order_id, changed_at);


-- =====================================================================
-- COMMENTS (tài liệu hóa, dbdiagram.io sẽ hiển thị)
-- =====================================================================
COMMENT ON TABLE  products              IS 'Sản phẩm shop bán; giá MVP theo VND nguyên. Inactive product không hiển thị catalog.';
COMMENT ON TABLE  customers             IS 'Optional - orders đã có customer snapshot fields.';
COMMENT ON TABLE  inventory_items       IS '1-1 với products. available = on_hand - reserved (tính runtime).';
COMMENT ON TABLE  orders                IS 'Đơn hàng theo VND nguyên. Snapshot customer + shipping address để bảo toàn dữ liệu lịch sử.';
COMMENT ON TABLE  order_items           IS 'Dòng sản phẩm theo VND nguyên; product_name + unit_price là snapshot tại lúc đặt.';
COMMENT ON TABLE  payments              IS 'Payment amount theo VND nguyên; quan hệ vật lý 1-N với orders; MVP chỉ tạo tối đa một attempt, retry là khả năng tương lai.';
COMMENT ON TABLE  stock_movements       IS 'Audit trail trung tâm. Mọi thay đổi stock đều ghi vào đây (BR-13).';
COMMENT ON TABLE  receiving_records     IS 'Nhập hàng từ supplier (FR-06).';
COMMENT ON TABLE  return_requests       IS 'Yêu cầu hoàn hàng. Chỉ tạo cho order Delivered (BR-08).';
COMMENT ON TABLE  return_request_items  IS 'Cho phép partial return theo từng order_item.';
COMMENT ON TABLE  delivery_status_history IS 'Audit trail mỗi lần đổi delivery status (FR-04.4).';


-- Customer identity foundation (SF-142); no demo seed in reference DDL.
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
