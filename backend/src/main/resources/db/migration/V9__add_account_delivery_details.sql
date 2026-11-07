-- SF-152: no backfill from historical order addresses.
CREATE TABLE account_delivery_details (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    receiver_name VARCHAR(255) NOT NULL CHECK (TRIM(receiver_name) <> ''),
    phone VARCHAR(20) NOT NULL CHECK (TRIM(phone) <> ''),
    address_line VARCHAR(500) NOT NULL CHECK (TRIM(address_line) <> ''),
    district VARCHAR(100),
    city VARCHAR(100) NOT NULL CHECK (TRIM(city) <> ''),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
