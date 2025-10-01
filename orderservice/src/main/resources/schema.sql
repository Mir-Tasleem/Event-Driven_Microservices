CREATE TABLE IF NOT EXISTS orders(
    id UUID PRIMARY KEY,
    customer_id UUID,
    status VARCHAR(255),
    total_amount DECIMAL(10,2),
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items(
    order_id UUID PRIMARY KEY,
    sku VARCHAR(255),
    quantity INT,
    price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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


