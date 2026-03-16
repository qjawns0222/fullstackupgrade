## 6. STATE MANAGEMENT APPROACHES

### 6.1 Spring State Machine (Job Applications)

**States:**
- APPLIED (initial), INTERVIEW, OFFER_RECEIVED, PASSED, REJECTED

**Persistence:**
- Redis via RedisStateMachineContextRepository
- Stored as JSON context

**Issues:**
- No optimistic locking on transitions (race condition risk)
- No event sourcing, state mutations are direct
- No compensation logic if transition fails
- Listener only logs, doesn't trigger business actions

### 6.2 Redis-Based State Caching

**OCR Cache**:
- Key: ocr:cache:{SHA256_hash}
- Value: OCR result text
- TTL: 7 days

**Idempotency Cache**:
- Key: idempotency:{idempotencyKey}
- Value: PROCESSING or result string
- TTL: 24 hours

**Rate Limit State**:
- Distributed via Bucket4j
- Per-IP limit: 20 requests/minute

### 6.3 Database State (JPA)

**Analysis Request Status**:
- PENDING → ANALYZING → COMPLETED/FAILED

**Job Application Status**:
- APPLIED → INTERVIEW → OFFER_RECEIVED → PASSED
- Alternative: APPLIED/INTERVIEW/OFFER_RECEIVED → REJECTED

**Issues**:
- No optimistic/pessimistic locking
- No audit trail of state transitions
- No compensation if save fails mid-operation

### 6.4 Event-Based State Transitions

**Spring Modulith Events**:
```
AiAnalysisEvent → AiAnalysisEventListener → async processing
ResumeSearchEvent → ResumeSearchEventListener
AuditLogMessage → AuditLogProducer → RabbitMQ → AuditLogConsumer
```

---

## 7. BACKGROUND JOB PROCESSING

### 7.1 Spring Batch (Tech Trend Analysis)

**Job: techTrendJob** (runs Monday 9 AM via ShedLock)

**Steps:**
1. **trendAnalysisStep** (chunk=100)
   - Reader: Resumes paginated from DB
   - Processor: Keyword extraction
   - Writer: Accumulate counts in ConcurrentHashMap

2. **saveTrendStatsStep** (tasklet)
   - Save aggregated stats to trend_stats table

3. **sendNotificationStep** (chunk=10)
   - Reader: All users
   - Writer: Queue emails via JobRunr

**Issues:**
- ConcurrentHashMap unbounded (memory leak risk)
- No restart capability
- No skip logic (single exception fails batch)
- Chunk size mismatch (analysis: 100, email: 10)

### 7.2 JobRunr (Async Email)

**Configuration**:
- Storage: SQL (MariaDB)
- Dashboard: localhost:8000
- Job: sendWeeklyReport with retries=5

**Issues**:
- Limited retry backoff customization
- No dead-letter handling
- No circuit breaker on SMTP
- Single-threaded by default

### 7.3 Scheduled Executors

**ModulithConfig**:
- Resubmit failed events older than 5 minutes (every 60s)
- Clean up completed events older than 7 days (every 60 minutes)
- ShedLock prevents duplicate execution in multi-instance setup

---

## 8. REAL-TIME COMMUNICATION

### 8.1 WebSocket (STOMP)

**Configuration**:
- Endpoint: /ws
- Allowed origins: * (WILDCARD - SECURITY RISK)
- SockJS fallback enabled
- Simple in-memory broker (not scalable)

**Topics**:
- /user/{username}/topic/analysis
- Message format: {requestId, status, message, timestamp}

**Issues**:
- In-memory broker: Messages lost on restart
- No cross-instance communication
- WILDCARD CORS allows any origin
- No per-message authentication

### 8.2 Server-Sent Events (SSE)

**SseEmitters**:
- Manages SseEmitter per user/tab
- Unbounded ConcurrentHashMap (memory leak risk)
- Manual cleanup on timeout/completion

**Issues**:
- No backpressure handling
- Thread-blocking if client slow
- No framework abstraction

### 8.3 Redis Pub/Sub (Legacy)

**Topic**: notification-topic
**Publisher**: AiAnalysisEventListener
**Message**: {username, content}

**Issues**:
- Dual-channel design (WebSocket + Redis)
- Fire-and-forget (no persistence)

---

## 9. CRITICAL BOTTLENECKS AT HIGH LOAD (10K+ Concurrent Requests)

### Rank 1: Database Connection Pool
**Current**: ~10 connections (HikariCP default)
**Failure**: Connection acquisition timeout, 503 errors
**Gap**: No explicit pool size configuration

### Rank 2: Redis Single Instance
**Current**: Standalone Redis
**Failure**: Complete cache/rate-limiting/idempotency failure
**Gap**: No Redis Sentinel or Cluster

### Rank 3: Email Executor Thread Pool
**Current**: Core=5, Max=10 threads, Queue=500
**Failure**: Queue exhaustion, RejectedExecutionException
**Gap**: No dynamic scaling, no fallback queue

### Rank 4: OCR Bulkhead
**Current**: maxConcurrentCalls=3
**Failure**: Request queuing, 2s wait timeout exhausted
**Gap**: No adaptive scaling

### Rank 5: WebSocket In-Memory Broker
**Current**: Simple broker (single JVM)
**Failure**: Connection limits, memory exhaustion
**Gap**: No distributed broker (RabbitMQ STOMP not configured)

### Rank 6: Elasticsearch
**Current**: No circuit breaker, no query timeout
**Failure**: Large result sets, OOM errors, slow query blocks threads
**Gap**: No timeout, no CB

### Rank 7: Idempotency Race Condition
**Current**: Redis check-then-act in AspectJ
```kotlin
val cachedValue = redisTemplate.opsForValue().get(redisKey)  // Check
if (cachedValue != null) return cachedValue
redisTemplate.opsForValue().set(redisKey, "PROCESSING", ...)  // Act (window here!)
val result = joinPoint.proceed()  // Two threads can both execute
```
**Gap**: Not atomic, no distributed lock

### Rank 8: Rate Limit Redis Failover
**Current**: If Redis unavailable, fails open (no rate limiting)
**Gap**: No local fallback cache

### Rank 9: RabbitMQ No Dead-Letter Queue
**Current**: Failed audit events logged, not persisted
**Gap**: No DLQ routing

### Rank 10: Batch Job Checkpointing
**Current**: No restart capability
**Gap**: If job fails at step 3, steps 1-2 re-execute wastefully

---

## 10. DATA CONSISTENCY ISSUES

### 10.1 Race Conditions in State Machine
- Two concurrent requests transition same job application
- No optimistic locking (version field)
- Possible inconsistent state in DB

### 10.2 Idempotency TOCTOU Vulnerability
- Race window between check and set
- Two requests with same key can both execute

### 10.3 Cache Invalidation
- Caffeine cache (500 entries) not invalidated on updates
- Stale data served across instances

### 10.4 Resume Indexing
- MariaDB save succeeds, ES indexing fails
- Inconsistent state (DB has data, ES doesn't)

### 10.5 Batch Job Failure Recovery
- If batch fails at email step, previous analysis is lost
- No checkpoint/restart capability

---

## 11. RESOURCE EXHAUSTION RISKS

### 11.1 Memory Leaks
- **SseEmitters**: Unbounded ConcurrentHashMap if cleanup fails
- **OCR temp files**: Disk space if exception before delete()
- **Batch job**: ConcurrentHashMap unbounded

### 11.2 Connection Pool Depletion
- **DB**: 10 connections exhausted in seconds
- **Redis**: No explicit pool, defaults to 8
- **S3**: AWS SDK connection pool not configured

### 11.3 File Descriptor Exhaustion
- OCR temp files
- WebSocket 10K connections = 10K+ file descriptors

### 11.4 Disk Space Exhaustion
- MinIO volume unbounded
- Temp file cleanup race condition
- ES indices no retention policy

### 11.5 GC Pressure
- High throughput (10K req/s) with small heap (default ~512MB)
- Caffeine cache (500 entries) doesn't evict properly

---

