CREATE TABLE IF NOT EXISTS stock(
sku VARCHAR(255) PRIMARY KEY,
available BIGINT,
reserved BIGINT
);

CREATE TABLE IF NOT EXISTS reservations(
id UUID PRIMARY KEY,
order_id VARCHAR(255),
sku VARCHAR(255),
quantity BIGINT,
status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS outbox(
    id UUID PRIMARY KEY,
    aggregate_id UUID,
    type VARCHAR(255),
    payload JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS processed_events(
    event_id UUID PRIMARY KEY,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


