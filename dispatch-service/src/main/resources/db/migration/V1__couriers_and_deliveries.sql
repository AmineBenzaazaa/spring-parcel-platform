CREATE TABLE courier (
    id     UUID PRIMARY KEY,
    name   VARCHAR(80) NOT NULL,
    phone  VARCHAR(20) NOT NULL,
    zone   VARCHAR(16) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_courier_zone ON courier (zone) WHERE active;

-- Ids match the courier users seeded by auth-service (courierId claim).
INSERT INTO courier (id, name, phone, zone) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Youssef El Amrani', '+212661000001', 'CASA'),
    ('c0000000-0000-0000-0000-000000000002', 'Hamza Bennani',     '+212661000002', 'CASA'),
    ('c0000000-0000-0000-0000-000000000003', 'Imane Tazi',        '+212661000003', 'RABAT'),
    ('c0000000-0000-0000-0000-000000000004', 'Karim Ouazzani',    '+212661000004', 'MARRAKECH'),
    ('c0000000-0000-0000-0000-000000000005', 'Nadia Alaoui',      '+212661000005', 'TANGIER'),
    ('c0000000-0000-0000-0000-000000000006', 'Omar Chraibi',      '+212661000006', 'KENITRA');

CREATE TABLE delivery (
    id                  UUID PRIMARY KEY,
    order_id            UUID          NOT NULL UNIQUE,  -- one delivery per order (also an idempotency backstop)
    tracking_code       VARCHAR(16)   NOT NULL,
    recipient_phone     VARCHAR(20),
    city                VARCHAR(64)   NOT NULL,
    zone                VARCHAR(16),
    cod_amount          NUMERIC(10, 2) NOT NULL,
    courier_id          UUID REFERENCES courier (id),
    status              VARCHAR(24)   NOT NULL,
    attempts            INT           NOT NULL DEFAULT 0,
    max_attempts        INT           NOT NULL DEFAULT 3,
    last_failure_reason VARCHAR(255),
    sequence            BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_delivery_courier_status ON delivery (courier_id, status);
