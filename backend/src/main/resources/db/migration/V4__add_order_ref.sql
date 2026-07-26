-- =====================================================================
-- Flyway migration V4 — Opaque order reference for guest payment (SF-73)
-- DBMS  : PostgreSQL 14+
-- Schema: shopflow (auto-managed by flyway.schemas in application.yaml)
--
-- WHY
-- Paying for an order is open to guests, so the payment endpoint cannot be
-- protected by a role. Addressing it by the sequential BIGSERIAL id let anyone
-- walk the id space and either mark a stranger's order paid, or fail it and
-- release the stock it was holding. The order reference is an unguessable
-- capability: you can only pay for an order whose reference you were given.
-- =====================================================================


ALTER TABLE orders ADD COLUMN order_ref VARCHAR(36);

-- Existing rows predate the column. gen_random_uuid() is volatile, so it is
-- evaluated per row and every order gets a distinct reference.
UPDATE orders SET order_ref = CAST(gen_random_uuid() AS VARCHAR(36)) WHERE order_ref IS NULL;

ALTER TABLE orders ALTER COLUMN order_ref SET NOT NULL;

-- Lưới an toàn cho mọi đường ghi không đi qua entity (seed, thao tác thủ công, test):
-- không đơn hàng nào có thể tồn tại mà thiếu tham chiếu.
ALTER TABLE orders ALTER COLUMN order_ref SET DEFAULT CAST(gen_random_uuid() AS VARCHAR(36));

CREATE UNIQUE INDEX uq_orders_order_ref ON orders (order_ref);
