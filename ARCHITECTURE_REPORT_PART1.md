# AIBlog Project - Comprehensive Architecture Analysis Report

## Executive Summary

The AIBlog project is a sophisticated multi-tier application built on Spring Boot 3.2.0 with Kotlin, featuring advanced resilience patterns, distributed event processing, OCR capabilities, and real-time communication. The architecture demonstrates enterprise-grade concerns including circuit breakers, distributed caching, state machines, batch processing, and comprehensive monitoring. However, under high-load scenarios (10,000+ concurrent requests), several critical bottlenecks and failure points would manifest requiring additional library support.

---

## 1. OVERALL ARCHITECTURE AND MODULE STRUCTURE

### 1.1 High-Level Architecture
- **Frontend**: Next.js 16 (React 19) with STOMP WebSocket client
- **API Gateway**: Spring Cloud Gateway with Circuit Breaker
- **Backend**: Spring Boot 3.2 with Kotlin, modular via Spring Modulith
- **Data Layer**: MariaDB (relational), Elasticsearch (search), Redis (cache/state)
- **Messaging**: RabbitMQ (async events), JobRunr (background jobs)
- **Storage**: MinIO S3-compatible object storage
- **Observability**: Prometheus, Grafana, Brave tracing

### 1.2 Core Modules
- **Analysis Module**: File upload → OCR → Resume creation
- **Job Application Module**: State machine for tracking job applications
- **Resume Module**: Full-text search via Elasticsearch
- **Audit Module**: Spring Modulith events → RabbitMQ → Elasticsearch
- **Authentication**: JWT + MFA (TOTP)
- **Batch Processing**: Weekly tech trend analysis via Spring Batch
- **Background Jobs**: Email notifications via JobRunr

---

## 2. ALL I/O POINTS

### 2.1 Database (MariaDB)

**Default HikariCP Configuration**
- Pool size: ~10 connections (DEFAULT, not explicitly configured)
- **CRITICAL BOTTLENECK**: Cannot serve 10,000 concurrent requests

**Tables**:
- users (id, username, password, role, email, mfa_secret, mfa_enabled)
- resumes (id, original_file_name, content, user_id, created_at)
- trend_stats (id, tech_stack, count, recorded_at)
- analysis_requests (id, original_file_name, file_key, status, result, created_at)
- job_applications (id, company_name, position, status, applied_date, memo, user_id, created_at, updated_at)
- JobRunr implicit tables

**Repository Patterns**:
- Standard Spring Data JPA (UserRepository, ResumeRepository)
- QueryDSL for complex searches (ResumeRepositoryImpl)
- No explicit pagination/batching for large result sets

### 2.2 External API Calls

#### MinIO S3
```
Endpoint: http://localhost:9000
Resilience:
  - @CircuitBreaker(name="s3Service"): failureRateThreshold=50%, wait=5s
  - @Retry(name="s3Service"): maxAttempts=3, exponential backoff 2x
Issue: No fallback strategy, circuit breaker fails fast
```

#### Elasticsearch
```
Spring Data Elasticsearch integration
Korean language support via Nori tokenizer
Issue: No circuit breaker, no query timeout, no connection pooling config
```

#### Gmail SMTP (Email)
```
Host: smtp.gmail.com:587
Timeout: 5s (socket, connection, write)
Async via ThreadPoolTaskExecutor: core=5, max=10, queue=500
Issue: Only 10 threads max for email, significant bottleneck
```

#### Tess4j OCR
```
Bulkhead: @Bulkhead(name="ocrService") maxConcurrentCalls=3, maxWaitDuration=2s
Circuit breaker: failureRateThreshold=50%
Issue: Only 3 concurrent OCR calls max, major bottleneck
```

### 2.3 File System Operations
- Temp files created for OCR processing
- Issue: No cleanup guarantee on exceptions, potential disk exhaustion
- No other direct file system operations

### 2.4 Message Queues (RabbitMQ)

**Topology**:
- Exchange: audit.exchange (TopicExchange)
- Queue: audit.queue (durable)
- Routing Key: audit.routing.key

**Issues**:
- No dead-letter queue for failed messages
- No explicit retry logic in consumer
- Single consumer instance (not horizontally scalable)
- No message TTL

### 2.5 Caching Layers (Multi-Level)

#### Redis (Distributed)
- Rate limiting bucket state (Bucket4j)
- Idempotency keys
- State machine context
- Session data
- **Issue**: Single instance, no clustering

#### Caffeine (Local In-Memory)
- Expiration: 60 minutes
- Size: 500 entries (maximum)
- **Issue**: Only 500 entries total, inadequate for multi-instance

#### OCR Cache (Redis)
- Key: ocr:cache:{SHA256_hash}
- TTL: 7 days
- **Feature**: Prevents redundant OCR

---

## 3. TECHNOLOGY STACK

| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.2.0 | Core framework |
| Kotlin | 1.9.20 | Language |
| Java | 17 | JVM |
| Spring Modulith | 1.1.0 | Modular events |
| Spring Data JPA | (bundled) | ORM |
| QueryDSL | 5.0.0 | Type-safe queries |
| Resilience4j | 2.2.0 | Circuit breaker, retry, bulkhead |
| Bucket4j | 8.10.1 | Rate limiting |
| Spring AMQP | (bundled) | RabbitMQ |
| JobRunr | 7.2.0 | Background jobs |
| Spring Batch | (bundled) | Batch processing |
| Spring State Machine | 4.0.0 | State transitions |
| Spring WebSocket | (bundled) | Real-time messaging |
| Tess4j | 5.10.0 | OCR (Tesseract) |
| AWS SDK v2 | 2.21.29 | S3/MinIO client |
| OpenPDF | 1.3.30 | PDF generation |
| Apache POI | 5.2.5 | Excel export |
| ShedLock | 5.12.0 | Distributed scheduling |
| Micrometer Brave | (bundled) | Distributed tracing |
| Unleash | 9.2.0 | Feature flags |

---

## 4. INFRASTRUCTURE COMPONENTS

### Docker Services:
- **Elasticsearch 8.x**: Single-node, no persistence volume
- **RabbitMQ 3.12**: Single instance, default credentials
- **Prometheus**: 15s scrape interval, no persistence
- **Grafana**: UI on port 3001, hardcoded credentials
- **MinIO**: Single instance, auto-creates aiblog-bucket
- **Redis**: Standalone, localhost:6379
- **MariaDB**: Standalone, localhost:3306/study_db

---

## 5. RESILIENCE PATTERNS (Current Implementation)

### 5.1 Circuit Breakers
- **S3 Service**: 50% failure threshold, 5s wait, 10 call window
- **OCR Service**: 50% failure threshold, 10s wait, 5 call window
- **Gateway Backend**: 50% failure threshold, 10s wait

### 5.2 Retry Logic
- **S3**: 3 attempts, exponential backoff (1s, 2s, 4s)
- **Email (JobRunr)**: 5 retries built-in

### 5.3 Bulkhead (Thread Pool Isolation)
- **OCR Service**: maxConcurrentCalls=3, maxWaitDuration=2s

### 5.4 Rate Limiting
- **Global**: 20 requests/minute per IP
- **Distributed**: Bucket4j + Redis
- **Issue**: If Redis down, rate limiting fails open

### 5.5 Idempotency
- **Pattern**: Idempotency-Key header
- **Storage**: Redis (PROCESSING state, result cache)
- **Issues**: Result cached as string only, TOCTOU race condition

### 5.6 Timeouts
- **SMTP**: 5s (socket, connection, write)
- **Gateway HTTP**: 1s connection, 5s response

---

