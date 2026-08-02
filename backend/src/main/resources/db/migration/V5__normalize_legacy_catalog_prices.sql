-- Normalize prices left by the pre-VND development catalog seed.
UPDATE products
SET price = CASE name
    WHEN '[DEV] Wireless Mouse' THEN 699000
    WHEN '[DEV] Mechanical Keyboard' THEN 2190000
    WHEN '[DEV] USB-C Hub' THEN 1290000
    WHEN '[DEV] Discontinued Webcam' THEN 899000
END
WHERE name IN (
    '[DEV] Wireless Mouse',
    '[DEV] Mechanical Keyboard',
    '[DEV] USB-C Hub',
    '[DEV] Discontinued Webcam'
)
  AND price IN (29.99, 89.90, 49.50, 39.00);
