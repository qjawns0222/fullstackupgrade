# 구현된 기능 목록

스킬이 새 기능을 구현할 때마다 이 파일을 업데이트한다.
새 미션 선정 시 이 목록을 먼저 읽어 중복을 피한다.

## 보안 / 인증
- JWT 인증 필터 + Refresh Token rotation (Redis)
- MFA/TOTP 2단계 인증 (dev.samstevens.totp)
- Guava BloomFilter 기반 토큰 블랙리스트 (Redis 전 인메모리 사전 필터)
- OWASP HTML Sanitizer XSS 방어 (policy-based: plain/rich/resume 3종)
- Rate Limiting — Bucket4j + Redis 분산 버킷 (annotation + interceptor)
- Idempotency Key — Redis 기반 중복 요청 차단 (@Idempotent AOP)

## 데이터 / 검색
- MariaDB + JPA/Hibernate + Flyway 스키마 관리
- QueryDSL 동적 쿼리 (이력서 복합 검색)
- Elasticsearch 이력서 전문 검색 (한국어 초성 지원)
- GraphQL API (Spring for GraphQL + DataLoader N+1 방지)
- JSON Schema 계약 검증 (networknt/json-schema-validator + @ValidateJsonSchema AOP)

## 캐시
- 2레벨 캐시: L1 Caffeine(JVM, 30s) + L2 Redisson(Redis, 300s) + pub/sub 무효화

## 비동기 / 이벤트
- Spring Modulith 이벤트 퍼블리케이션 (@ApplicationModuleListener + JPA outbox)
- RabbitMQ 감사 로그 파이프라인 (AuditLogAspect → RabbitMQ → ES 인덱싱)
- Webhook 발송 시스템 — OkHttp3 HMAC 서명, 지수 백오프 재시도, 발송 로그

## 파일 처리
- MinIO(S3 호환) 파일 업로드/다운로드 (Resilience4j Circuit Breaker + Retry)
- Tesseract OCR 이력서 분석 (Bulkhead 동시성 제한 3개)
- PDF 생성 (OpenPDF)
- Excel 내보내기 (Apache POI)

## 상태 머신 / 분산 처리
- Spring Statemachine 채용 상태 머신 (Redis 영속성)
- Saga 패턴 분산 트랜잭션 (S3 업로드 → DB 저장 → ES 인덱싱, 보상 트랜잭션 포함)
- ShedLock 분산 락 (Redis, 스케줄러 중복 실행 방지)
- Spring Retry @RetryOnDeadlock (DB 데드락 자동 재시도)

## 스케줄링 / 배치
- Spring Batch 주간 기술 트렌드 분석 Job
- JobRunr 비동기 이메일 발송 (대시보드 포트 8000)
- BatchScheduler (ShedLock 연동)

## 모니터링 / 관찰가능성
- MDC 기반 구조화 JSON 로깅 (logstash-logback-encoder → Elasticsearch)
- datasource-proxy 슬로우 쿼리 감지 (>300ms) + N+1 패턴 탐지 (임계값 5회)
- 슬로우 쿼리 자동 EXPLAIN 분석 + 인덱스 추천 (JSqlParser WHERE 컬럼 파싱, 풀스캔 감지, ES 저장)
  - ExplainResultStore fun interface 포트로 ES 격리
  - GET /api/query-monitor/explain-history, /{id} API
  - 프론트엔드 /query-monitor 대시보드에 EXPLAIN 히스토리 섹션 추가
- Micrometer + Prometheus 메트릭 수집 (Grafana 연동)
- Distributed Tracing — micrometer-tracing-bridge-brave + Zipkin
- ObservedAspect 활성화 (@Observed AOP) + HTTP 슬라이딩 윈도우 성능 통계
  - P50/P95/P99 레이턴시, 에러율, 엔드포인트별 5분 윈도우 집계
  - /api/perf/stats, /api/perf/summary API
  - 프론트엔드: /perf-stats 대시보드

## 메시지 큐 / 신뢰성
- RabbitMQ Dead Letter Queue 자동 관리
  - audit.queue에 x-dead-letter-exchange/routing-key/message-ttl 설정
  - DLQ 수신 → dlq_messages 테이블 자동 적재 (DlqMonitorService @RabbitListener)
  - PENDING / RETRYING / RESOLVED / DISCARDED 상태 머신
  - 단건 재처리(POST /api/dlq/{id}/retry), 전체 재처리(POST /api/dlq/retry-all), 폐기(POST /api/dlq/{id}/discard)
  - GET /api/dlq (페이징+상태필터), GET /api/dlq/stats 통계 API
  - 프론트엔드: /admin/dlq 대시보드 (실시간 5s polling, 통계 카드, 단건/전체 재처리)

## 모니터링 / 에러 추적
- Sentry 에러 추적 + 세션 리플레이 (sentry-spring-boot-starter-jakarta + @sentry/nextjs)
  - 백엔드: GlobalExceptionHandler에서 500 에러 자동 캡처, SentryContextFilter로 requestId/userId scope 주입
  - 프론트엔드: React ErrorBoundary, 에러 세션 100% 리플레이, /admin/sentry 대시보드

## 기타 인프라
- Spring Modulith 모듈 경계 검증 (ArchUnit)
- OpenAPI/Swagger UI
- WebSocket STOMP 실시간 알림
- Feature Flags (Unleash)
- Thymeleaf 이메일 템플릿
