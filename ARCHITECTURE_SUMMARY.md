# AIBlog Architecture - Executive Summary

## Project Overview
- **Type**: Full-stack web application (Spring Boot 3.2 + Next.js 16)
- **Architecture**: Modular microservices-like with Spring Modulith
- **Primary Language**: Kotlin (backend), TypeScript (frontend)
- **Scale Target**: 10,000+ concurrent users
- **Status**: MVP to Production-ready

## Core Technology Stack
- **Backend**: Spring Boot 3.2, Kotlin 1.9, Spring Modulith 1.1
- **Data**: MariaDB, Elasticsearch, Redis, MinIO S3
- **Messaging**: RabbitMQ, JobRunr
- **Resilience**: Resilience4j, Bucket4j, ShedLock
- **Observability**: Prometheus, Grafana, Brave Tracing
- **Real-Time**: WebSocket (STOMP), SSE, Redis Pub/Sub

## Key Features
1. **AI-Powered Resume Analysis**: OCR processing, PDF generation
2. **Job Application Tracking**: State machine-based workflow
3. **Resume Search**: Elasticsearch full-text search (Korean language support)
4. **Audit Logging**: Spring Modulith events to RabbitMQ to Elasticsearch
5. **Background Jobs**: Tech trend analysis via Spring Batch
6. **Multi-Factor Authentication**: TOTP support
7. **Rate Limiting**: Distributed via Redis (Bucket4j)
8. **Idempotency**: Redis-based idempotency keys

## Critical Issues for Production Readiness

### 1. CONNECTION POOLING (CRITICAL)
- Current: ~10 HikariCP connections
- Needed: 50-100+ for 10K concurrent requests
- Impact: Complete service degradation at high load

### 2. DISTRIBUTED CACHE (HIGH)
- Current: Standalone Redis
- Issue: Single point of failure
- Impact: Cache/rate-limiting/idempotency all fail

### 3. ASYNC EXECUTION (HIGH)
- Current: Email executor 10 threads max, queue 500
- Issue: Queue exhaustion under load
- Impact: Email notifications dropped

### 4. OCR PROCESSING (MEDIUM)
- Current: Bulkhead max 3 concurrent
- Issue: Significant bottleneck
- Impact: 2s+ wait times per OCR request

### 5. WEBSOCKET SCALING (MEDIUM)
- Current: In-memory broker (single JVM)
- Issue: Doesn't scale across instances
- Impact: Message loss, connection limits

### 6. DATA CONSISTENCY (MEDIUM)
- Idempotency: TOCTOU race condition
- State transitions: No optimistic locking
- Batch jobs: No restart/compensation logic

### 7. OBSERVABILITY (MEDIUM)
- Missing: Structured logging, custom business metrics
- Impact: Difficult to diagnose issues in production

## Required Library Additions (by priority)

### Phase 1: Critical (Immediate)
1. **HikariCP explicit tuning** - Pool size 50+
2. **Redis Sentinel/Cluster** - High availability
3. **Spring Retry** - Deadlock recovery
4. **Distributed Lock** - Atomic idempotency
5. **Logstash encoder** - Structured logging

### Phase 2: High Priority
6. **Circuit Breaker for Elasticsearch**
7. **Spring Cloud Stream** - Dead-letter queues
8. **Endpoint Rate Limiter** - Resilience4j RateLimiter
9. **Batch Job Restart** - Spring Batch checkpointing
10. **Reactive WebSocket** - Spring WebFlux

### Phase 3: Medium Priority
11. **Axon Framework** - Event sourcing, sagas
12. **Custom Metrics** - Business KPIs
13. **Chaos Engineering** - Failure testing
14. **Load Testing** - Gatling/JMeter
15. **Spring Cloud Config** - Centralized config

## Architecture Strengths
- Well-structured modularity with Spring Modulith
- Comprehensive resilience patterns (CB, retry, bulkhead)
- Distributed tracing integration (Brave)
- Event-driven audit logging
- Feature flag support (Unleash)
- Batch processing framework (Spring Batch)
- State machine for workflows

## Key Recommendations
1. Implement connection pooling configuration immediately
2. Add Redis Sentinel for high availability
3. Implement distributed locking for idempotency
4. Add structured logging across all services
5. Test failure scenarios (RabbitMQ down, ES down, Redis down)
6. Monitor connection pool exhaustion
7. Configure explicit query timeouts
8. Implement per-endpoint rate limiting

## Estimated Production Hardening Timeline
- **Week 1-2**: Connection pooling, Redis HA, structured logging
- **Week 3-4**: Circuit breakers (ES), batch restart, DLQ
- **Week 5-6**: Event sourcing, custom metrics, load testing
- **Week 7-8**: Chaos tests, log aggregation, config mgmt

**Total Effort**: 3-4 sprints (engineer time: 2-3 FTE)

## Files in This Report
1. `ARCHITECTURE_REPORT_PART1.md` - Overview, modules, I/O points, technology stack
2. `ARCHITECTURE_REPORT_PART2.md` - State management, background jobs, real-time comms, bottlenecks
3. `ARCHITECTURE_REPORT_PART3.md` - Detailed library gaps, implementation priority, summary table

