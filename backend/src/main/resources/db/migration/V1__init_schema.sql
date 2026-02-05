CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(20),
    password VARCHAR(255),
    role VARCHAR(255) NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    content TEXT,
    user_id BIGINT,
    created_at DATETIME,
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE trend_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tech_stack VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL
);

CREATE TABLE analysis_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    result TEXT,
    created_at DATETIME
);

CREATE TABLE job_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    position VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    applied_date DATE,
    memo TEXT,
    user_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_job_applications_user FOREIGN KEY (user_id) REFERENCES users (id)
);
