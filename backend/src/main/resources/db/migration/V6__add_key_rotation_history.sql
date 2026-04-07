CREATE TABLE key_rotation_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rotated_at   DATETIME(6)  NOT NULL,
    key_count    INT          NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL,
    error_message TEXT,
    INDEX idx_rotated_at (rotated_at),
    INDEX idx_status (status)
);
