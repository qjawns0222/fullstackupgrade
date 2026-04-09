CREATE TABLE key_rotation_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rotated_at   DATETIME(6)  NOT NULL,
    key_count    INT          NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_rotated_at ON key_rotation_history (rotated_at);
CREATE INDEX idx_status ON key_rotation_history (status);
