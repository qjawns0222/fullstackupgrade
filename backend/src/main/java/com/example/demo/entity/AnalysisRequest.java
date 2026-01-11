package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_requests")
public class AnalysisRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String result;

    private LocalDateTime createdAt;

    // Status Enum definition
    public enum Status {
        PENDING,
        ANALYZING,
        COMPLETED,
        FAILED
    }

    public AnalysisRequest(String originalFileName) {
        this.originalFileName = originalFileName;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void startAnalysis() {
        this.status = Status.ANALYZING;
    }

    public void complete(String result) {
        this.status = Status.COMPLETED;
        this.result = result;
    }

    public void fail(String errorMessage) {
        this.status = Status.FAILED;
        this.result = errorMessage;
    }
}
