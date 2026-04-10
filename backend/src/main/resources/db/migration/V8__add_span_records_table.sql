CREATE TABLE span_records (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    span_name     VARCHAR(200) NOT NULL,
    class_name    VARCHAR(200) NOT NULL,
    method_name   VARCHAR(200) NOT NULL,
    duration_ms   BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500),
    recorded_at   DATETIME(6)  NOT NULL
);

CREATE INDEX idx_span_records_recorded_at ON span_records (recorded_at DESC);
CREATE INDEX idx_span_records_status ON span_records (status);
CREATE INDEX idx_span_records_duration ON span_records (duration_ms DESC);
