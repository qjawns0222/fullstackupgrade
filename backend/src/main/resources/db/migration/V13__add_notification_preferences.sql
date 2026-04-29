CREATE TABLE user_notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE UNIQUE INDEX uq_user_channel ON user_notification_preferences (user_id, channel);
CREATE INDEX idx_notif_pref_user_id ON user_notification_preferences (user_id);
