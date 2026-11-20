CREATE TABLE parcel_order (
    id                     UUID PRIMARY KEY,
    tracking_code          VARCHAR(16)   NOT NULL UNIQUE,
    merchant_id            VARCHAR(32)   NOT NULL,
    recipient_name         VARCHAR(120)  NOT NULL,
    recipient_phone        VARCHAR(20)   NOT NULL,
    address_line           VARCHAR(255)  NOT NULL,
    city                   VARCHAR(64)   NOT NULL,
    cod_amount             NUMERIC(10, 2) NOT NULL CHECK (cod_amount >= 0),
    status                 VARCHAR(24)   NOT NULL,
    delivery_attempts      INT           NOT NULL DEFAULT 0,
    last_delivery_sequence BIGINT        NOT NULL DEFAULT 0,
    cancel_reason          VARCHAR(255),
    idempotency_key        VARCHAR(64),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    -- a retried POST with the same Idempotency-Key returns the original order instead of a duplicate
    CONSTRAINT uq_order_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_order_merchant_created ON parcel_order (merchant_id, created_at DESC);
