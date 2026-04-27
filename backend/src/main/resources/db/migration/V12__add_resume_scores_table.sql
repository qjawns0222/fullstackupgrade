CREATE TABLE resume_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_request_id BIGINT NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    total_score INT NOT NULL,
    skill_score INT NOT NULL,
    experience_score INT NOT NULL,
    education_score INT NOT NULL,
    extracted_skills TEXT,
    extracted_experience TEXT,
    extracted_education TEXT,
    summary TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_resume_scores_analysis FOREIGN KEY (analysis_request_id) REFERENCES analysis_requests(id)
);

CREATE INDEX idx_resume_scores_request_id ON resume_scores (analysis_request_id);
