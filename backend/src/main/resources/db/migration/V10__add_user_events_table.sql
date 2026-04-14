CREATE TABLE user_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(100) NOT NULL,
    user_id     VARCHAR(100),
    event_type  VARCHAR(50)  NOT NULL,
    resource_id VARCHAR(200),
    metadata    TEXT,
    occurred_at DATETIME(6)  NOT NULL
);

CREATE INDEX idx_user_events_session_id  ON user_events (session_id);
CREATE INDEX idx_user_events_event_type  ON user_events (event_type);
CREATE INDEX idx_user_events_occurred_at ON user_events (occurred_at DESC);
CREATE INDEX idx_user_events_user_id     ON user_events (user_id);
