CREATE TABLE webhook_endpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_url VARCHAR(2048) NOT NULL,
    secret VARCHAR(512) NOT NULL,
    event_types VARCHAR(1024) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_webhook_endpoint_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE webhook_delivery_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    http_status INT,
    response_body TEXT,
    attempt_count INT NOT NULL DEFAULT 0,
    delivered_at DATETIME,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_webhook_delivery_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id)
);
