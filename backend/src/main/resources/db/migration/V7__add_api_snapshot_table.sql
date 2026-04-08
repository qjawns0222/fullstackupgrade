CREATE TABLE api_snapshots (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    version     VARCHAR(50)  NOT NULL,
    spec_json   TEXT         NOT NULL,
    created_at  DATETIME(6)  NOT NULL
);

CREATE TABLE api_breaking_changes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    old_version     VARCHAR(50)  NOT NULL,
    new_version     VARCHAR(50)  NOT NULL,
    change_type     VARCHAR(100) NOT NULL,
    description     TEXT         NOT NULL,
    element         VARCHAR(500),
    detected_at     DATETIME(6)  NOT NULL
);
