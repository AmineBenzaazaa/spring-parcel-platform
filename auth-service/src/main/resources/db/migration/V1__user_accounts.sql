CREATE TABLE user_account (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OPS', 'COURIER', 'MERCHANT')),
    merchant_id   VARCHAR(32),                    -- set for MERCHANT users
    courier_id    UUID,                           -- set for COURIER users (id in dispatch-service)
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL
);
