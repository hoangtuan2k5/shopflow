-- =====================================================================
-- ShopFlow — Database Schema (MVP)
-- Target DBMS  : PostgreSQL 14+
-- Schema       : shopflow
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
-- 6. PAYMENTS  (1-N với orders để hỗ trợ retry)
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
COMMENT ON TABLE  products              IS 'Sản phẩm shop bán. Inactive product không hiển thị catalog.';
COMMENT ON TABLE  customers             IS 'Optional - orders đã có customer snapshot fields.';
COMMENT ON TABLE  inventory_items       IS '1-1 với products. available = on_hand - reserved (tính runtime).';
COMMENT ON TABLE  orders                IS 'Đơn hàng. Snapshot customer + shipping address để bảo toàn dữ liệu lịch sử.';
COMMENT ON TABLE  order_items           IS 'Dòng sản phẩm trong order. product_name + unit_price là snapshot tại lúc đặt.';
COMMENT ON TABLE  payments              IS '1-N với orders để hỗ trợ retry payment.';
COMMENT ON TABLE  stock_movements       IS 'Audit trail trung tâm. Mọi thay đổi stock đều ghi vào đây (BR-13).';
COMMENT ON TABLE  receiving_records     IS 'Nhập hàng từ supplier (FR-06).';
COMMENT ON TABLE  return_requests       IS 'Yêu cầu hoàn hàng. Chỉ tạo cho order Delivered (BR-08).';
COMMENT ON TABLE  return_request_items  IS 'Cho phép partial return theo từng order_item.';
COMMENT ON TABLE  delivery_status_history IS 'Audit trail mỗi lần đổi delivery status (FR-04.4).';
