# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AIBlog is a full-stack application with three runnable components:
- **`backend/`** — Spring Boot 3.2 / Kotlin 1.9 (port 8080), main application
- **`gateway/`** — Spring Cloud Gateway / Kotlin (port 8090), JWT auth filter + circuit breaker
- **`frontend/`** — Next.js 16 / React 19 / TypeScript (port 3000)

Infrastructure (via `docker-compose.yml`): Elasticsearch (9200), RabbitMQ (5672/15672), Prometheus (9090), Grafana (3001), MinIO (9000/9001). MariaDB and Redis run locally (not in compose).

## Commands

### Backend (Spring Boot)
```bash
cd backend
./gradlew build            # compile + test
./gradlew bootRun          # run dev server
./gradlew test             # all tests
./gradlew test --tests "com.example.demo.SomeTest"  # single test class
```

### Gateway
```bash
cd gateway
./gradlew bootRun
./gradlew test
```

### Frontend
```bash
cd frontend
npm install
npm run dev        # dev server on :3000
npm run build      # production build
npm run lint       # ESLint
npm run post-tistory  # publish draft to Tistory via ts-node
```

### Infrastructure
```bash
docker-compose up -d    # start ES, RabbitMQ, Prometheus, Grafana, MinIO
```

JobRunr dashboard runs on port 8000 when the backend is up.

## Architecture

### Request Flow
`Browser → Gateway (:8090) → Backend (:8080)`

The gateway validates JWTs in `AuthorizationHeaderFilter` and adds the decoded user info as a forwarded header. Circuit breaker (Resilience4j via WebFlux) sits between gateway and backend.

### Backend Module Structure

`com.example.demo` contains these packages (Spring Modulith enforces inter-module boundaries):

| Package | Responsibility |
|---|---|
| `analysis` | File upload → Tesseract OCR → resume creation; bulkhead limits 3 concurrent OCR calls |
| `application` | Job application state machine (Spring Statemachine + Redis persistence) |
| `audit` | Spring Modulith event listeners → RabbitMQ → Elasticsearch indexing |
| `cache` | Two-level cache: L1 Caffeine (JVM-local, 30s TTL) + L2 Redisson (Redis, 300s TTL) with pub/sub invalidation |
| `graphql` | Spring for GraphQL resolvers + DataLoader (N+1 prevention); schema in `resources/graphql/*.graphqls` |
| `query` | datasource-proxy setup: `SlowQueryListener` detects slow queries (>300ms) and N+1 patterns via `QueryExecutionContext` (ThreadLocal normalization) |
| `saga` | Distributed transaction compensation using manual saga pattern |
| `scheduler` | Spring Batch jobs (weekly tech trend analysis) + ShedLock for distributed locking |
| `security` | JWT filter chain, MFA/TOTP, `@PreAuthorize` guards |
| `shared` | Idempotency keys (Redis), rate limiting (Bucket4j + Redis), feature flags (Unleash) |

### Data Layer
- **MariaDB** via JPA/Hibernate; schema managed by Flyway (`resources/db/migration/`)
- **QueryDSL** for complex resume searches (`ResumeRepositoryImpl`)
- **Elasticsearch** for full-text resume search (Korean language support) and audit log storage; index mappings in `resources/es/`
- **Redis** for: session/state (Spring Statemachine), cache (Redisson), rate limiting (Bucket4j), idempotency keys, ShedLock, cache invalidation pub/sub

### Frontend Structure
Next.js App Router under `src/app/`. Key routes:
- `/admin/*` — admin panels (audit, jobs, saga, graphql demo, feature flags, deadlock testing)
- `/query-monitor` — real-time slow query / N+1 alert dashboard (5s polling)
- `/resumes`, `/applications`, `/analysis` — main user features
- `/cache`, `/idempotency`, `/monitoring` — feature demo pages

State management: TanStack Query for server state. WebSocket via STOMP over SockJS for real-time updates.

### Configuration Notes
- Backend `application.yml` has hardcoded local DB credentials (`root/root`, `localhost:3306/study_db`) and a Gmail app password — these are dev defaults, not production secrets.
- Two-level cache names must match between `application.yml` (`cache.two-level.cache-names`) and `@Cacheable` annotations.
- `query.monitor.slow-query-threshold-ms` (default 300) and `query.monitor.n1-threshold-count` (default 5) are configurable.

### Testing Patterns
- Use fake implementations over Mockito for Kotlin tests (Mockito `ArgumentCaptor` with `never()` causes NPE with Kotlin non-null types). See `FakeQueryInspector` pattern.
- ArchUnit tests validate module boundary rules.
- Testcontainers used for RabbitMQ integration tests.
- H2 in-memory DB for JPA unit tests.
- `spring-modulith-test` for module integration tests.

### Blog Drafts
`blog_draft.md` contains Korean-language technical blog posts documenting implemented features. This is the primary content artifact of the project — implementations are built to be written about.
