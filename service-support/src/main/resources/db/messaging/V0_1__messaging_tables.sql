-- Shared messaging tables, applied by every service that uses service-support's Flyway location.

-- Transactional outbox: rows are written in the SAME transaction as the business change,
-- then a relay publishes them to Kafka. Guarantees "DB commit <=> event eventually published".
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,           -- = envelope eventId
    seq            BIGSERIAL    NOT NULL UNIQUE, -- publish order
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,      -- Kafka key
    event_type     VARCHAR(64)  NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    payload        TEXT         NOT NULL,      -- full envelope JSON
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (seq) WHERE published_at IS NULL;

-- Idempotent consumer: one row per (event, consumer). The primary key is the dedup guarantee.
CREATE TABLE processed_event (
    event_id     UUID        NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
