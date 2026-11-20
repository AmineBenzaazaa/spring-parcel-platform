CREATE TABLE notification (
    id            UUID PRIMARY KEY,
    event_id      UUID         NOT NULL,
    order_id      UUID         NOT NULL,
    tracking_code VARCHAR(16),
    channel       VARCHAR(16)  NOT NULL,
    recipient     VARCHAR(32),
    message       VARCHAR(500) NOT NULL,
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    provider_ref  VARCHAR(64),
    error         VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_notification_event_channel UNIQUE (event_id, channel)
);
CREATE INDEX idx_notification_order ON notification (order_id, created_at);
