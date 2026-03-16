# AIBlog Production Hardening - Implementation Roadmap

## Quick Start: Top 5 Priority Items

### 1. HikariCP Connection Pool (Immediate)
```yaml
spring.datasource.hikari.maximum-pool-size: 50
spring.datasource.hikari.minimum-idle: 10
```

### 2. Redis Sentinel Setup
Add sentinel instances to docker-compose.yml for high availability

### 3. Spring Retry for Deadlocks
```gradle
implementation 'org.springframework.retry:spring-retry'
```

### 4. Structured Logging
```gradle
implementation 'net.logstash.logback:logstash-logback-encoder:7.3'
```

### 5. Distributed Lock for Idempotency
Fix TOCTOU race in IdempotencyAspect using ShedLock provider

## Phase 1: Connection & Resilience (Week 1-2)

### Issues to Address:
- Database connection pool only 10 connections
- No query timeouts
- No deadlock retry logic
- Plain text logs, no correlation

### Files to Modify:
- `application.yml` - HikariCP, Hibernate config
- `AsyncConfig.kt` - Already good, no changes needed
- `logback-spring.xml` - Add JSON encoder
- `IdempotencyAspect.kt` - Fix TOCTOU race
- `docker-compose.yml` - Add Redis Sentinel

## Phase 2: Event & Observability (Week 3-4)

### Issues to Address:
- No dead-letter queue for failed events
- Elasticsearch has no circuit breaker
- No custom business metrics
- Batch jobs can't restart

### Libraries to Add:
- `org.springframework.cloud:spring-cloud-starter-stream-rabbit`
- Custom Resilience4j wrapper for Elasticsearch

## Phase 3: Scale Testing (Week 5-6)

### Issues to Address:
- No load test baseline
- No chaos testing
- No performance metrics validation

### Tools to Add:
- Gatling for load testing
- Custom chaos scenario scripts

## Critical Success Metrics:
- 95th percentile latency < 500ms at 10K concurrent
- Connection pool < 50 utilization under normal load
- 0% message loss in audit events
- Idempotency enforced atomically
- Circuit breakers activate on failure

## Implementation Timeline
- Week 1-2: Connection pooling, logging, retry logic
- Week 3-4: Event resilience, metrics, circuit breakers
- Week 5-6: Load testing, optimization
- Week 7-8: Production validation, runbooks

Total effort: 3-4 sprints (2-3 FTE engineers)

