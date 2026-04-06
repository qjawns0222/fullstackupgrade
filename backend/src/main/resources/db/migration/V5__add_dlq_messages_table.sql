CREATE TABLE dlq_messages (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            VARCHAR(50)  NOT NULL,
    action             VARCHAR(100) NOT NULL,
    description        TEXT         NOT NULL,
    params             TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    error_message      TEXT,
    original_timestamp DATETIME(6)  NOT NULL,
    dlq_status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    failed_at          DATETIME(6)  NOT NULL,
    resolved_at        DATETIME(6),
    retry_count        INT          NOT NULL DEFAULT 0,
    last_error         TEXT,
    INDEX idx_dlq_status (dlq_status),
    INDEX idx_failed_at (failed_at)
);
