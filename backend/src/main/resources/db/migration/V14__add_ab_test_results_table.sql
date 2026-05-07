CREATE TABLE IF NOT EXISTS ab_test_results (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    toggle_name  VARCHAR(100) NOT NULL,
    variant_name VARCHAR(100) NOT NULL,
    user_id      VARCHAR(100),
    session_id   VARCHAR(100),
    payload      TEXT,
    recorded_at  DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_abt_toggle_recorded ON ab_test_results (toggle_name, recorded_at);
