CREATE TABLE domain_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_payload TEXT NOT NULL,
    actor VARCHAR(100),
    occurred_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_domain_events_aggregate ON domain_events (aggregate_type, aggregate_id);
CREATE INDEX idx_domain_events_occurred_at ON domain_events (occurred_at);
