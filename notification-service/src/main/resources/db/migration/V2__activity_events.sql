-- Ops activity log: every order / delivery event seen on Kafka, kept for the dashboard's order
-- timeline. The primary key is the envelope eventId, so a redelivered event is stored once.
CREATE TABLE activity_event (
    event_id        UUID PRIMARY KEY,
    order_id        UUID         NOT NULL,
    tracking_code   VARCHAR(16),
    event_type      VARCHAR(48)  NOT NULL,
    status          VARCHAR(24),
    previous_status VARCHAR(24),
    attempt         INT          NOT NULL DEFAULT 0,
    reason          VARCHAR(255),
    courier_id      UUID,
    correlation_id  VARCHAR(64),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_activity_order ON activity_event (order_id, occurred_at);
