INSERT INTO products (name, description, price, active, low_stock_threshold)
SELECT '[DEV] Wireless Mouse', 'Ergonomic wireless mouse with USB receiver.', 29.99, TRUE, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '[DEV] Wireless Mouse');

INSERT INTO products (name, description, price, active, low_stock_threshold)
SELECT '[DEV] Mechanical Keyboard', 'Compact mechanical keyboard with tactile switches.', 89.90, TRUE, 3
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '[DEV] Mechanical Keyboard');

INSERT INTO products (name, description, price, active, low_stock_threshold)
SELECT '[DEV] USB-C Hub', 'Seven-port USB-C hub for laptops and tablets.', 49.50, TRUE, 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '[DEV] USB-C Hub');

INSERT INTO products (name, description, price, active, low_stock_threshold)
SELECT '[DEV] Discontinued Webcam', 'Inactive product used to verify catalog filtering.', 39.00, FALSE, 2
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '[DEV] Discontinued Webcam');

INSERT INTO inventory_items (product_id, on_hand_stock, reserved_stock)
SELECT id, 25, 3 FROM products p
WHERE p.name = '[DEV] Wireless Mouse'
  AND NOT EXISTS (SELECT 1 FROM inventory_items i WHERE i.product_id = p.id);

INSERT INTO inventory_items (product_id, on_hand_stock, reserved_stock)
SELECT id, 5, 5 FROM products p
WHERE p.name = '[DEV] Mechanical Keyboard'
  AND NOT EXISTS (SELECT 1 FROM inventory_items i WHERE i.product_id = p.id);
