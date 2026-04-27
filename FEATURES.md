# 구현된 기능 목록

스킬이 새 기능을 구현할 때마다 이 파일을 업데이트한다.
새 미션 선정 시 이 목록을 먼저 읽어 중복을 피한다.

## AI / LLM 연동
- AI 기반 이력서 스코어링 (Spring AI + GPT-4o-mini)
  - LlmScoringClient 포트 인터페이스 + SpringAiScoringClient 어댑터 (ChatClient + BeanOutputConverter)
  - ResumeScoreStore 포트 인터페이스 + JpaResumeScoreStore 어댑터 (Flyway V12 resume_scores 테이블)
  - ResumeScoringService: 스킬(40%) + 경력(40%) + 학력(20%) 가중 평균으로 0-100 점수 산출, coerceIn 범위 보장
  - POST /api/scoring (이력서 텍스트 + 직무명 → 점수), GET /api/scoring (전체 목록), GET /api/scoring/request/{id}
  - 프론트엔드: /admin/resume-scoring 대시보드 (점수 바 차트, 통계 카드, 5s polling, 상세 펼치기)

## 사용자 행동 분석
- 퍼널 분석 (이력서 조회→저장→다운로드 전환율 추적)
  - UserEvent JPA 엔티티 + Flyway V10 user_events 테이블 (session_id/user_id/event_type/resource_id)
  - UserEventStore 포트 인터페이스 + JpaUserEventStore 어댑터 (JPQL COUNT DISTINCT 집계)
  - FunnelAnalysisService: RESUME_VIEW→SAVE→DOWNLOAD 단계별 세션 수 + 전환율 계산
  - POST /api/funnel/events (이벤트 기록), GET /api/funnel/stats?periodHours= (퍼널 통계)
  - 프론트엔드: /admin/funnel 대시보드 (퍼널 바 차트, 이탈 수 표시, 기간 선택, 5s polling)

## 모니터링 / 트레이싱
- 메서드 레벨 타이밍 추적 (Spring AOP + @WithSpan)
  - @WithSpan 어노테이션으로 메서드 실행 시간 자동 측정, SUCCESS/SLOW/ERROR 상태 분류
  - SpanStore 포트 인터페이스 + JpaSpanStore 어댑터 (Flyway V8 span_records 테이블)
  - GET /api/tracing/stats, /recent, /slow REST API
  - 프론트엔드: /admin/tracing 대시보드 (5s polling, 최근 Span/SLOW Span 탭 전환)
- 분산 추적 Baggage 전파 (Micrometer Baggage API + W3C Baggage)
  - BaggageContextHolder: Micrometer Tracer API로 userId/tenantId 설정·조회
  - BaggageMessagePostProcessor: RabbitMQ convertAndSend 시 AMQP 메시지 헤더에 Baggage 삽입
  - BaggageTaskDecorator: @Async 스레드 전환 시 Baggage 캡처 후 워커 스레드에서 복원
  - BaggageAmqpListener: 수신 AMQP 메시지 헤더에서 Baggage 복원 유틸리티
  - management.tracing.baggage.remote-fields + correlation.fields 설정으로 HTTP/MDC 자동 전파
  - GET /api/baggage/current, /snapshot, POST /api/baggage/set REST API
  - 프론트엔드: /admin/baggage 대시보드 (Baggage 실시간 조회·설정, 전파 경로 설명)

## API 변경 관리
- API Breaking Change 자동 감지 (openapi-diff-core 2.1.0)
  - ApplicationReadyEvent 시 /v3/api-docs 스냅샷 자동 캡처 + DB 저장
  - 직전 스냅샷과 OpenApiCompare로 비교: ENDPOINT_REMOVED, PARAMETER_ADDED_REQUIRED, PARAMETER_REMOVED, RESPONSE_SCHEMA_CHANGED, REQUEST_BODY_CHANGED 5가지 유형 감지
  - GET /api/api-changes/breaking, /stats, /snapshots, /breaking/between API
  - 프론트엔드: /admin/api-changes 대시보드 (Breaking Change 목록 + 스냅샷 이력 탭)

## 보안 / 인증
- JWT 인증 필터 + Refresh Token rotation (Redis)
- MFA/TOTP 2단계 인증 (dev.samstevens.totp)
- Guava BloomFilter 기반 토큰 블랙리스트 (Redis 전 인메모리 사전 필터)
- OWASP HTML Sanitizer XSS 방어 (policy-based: plain/rich/resume 3종)
- Rate Limiting — Bucket4j + Redis 분산 버킷 (annotation + interceptor)
- Circuit Breaker 상태 기반 적응형 Rate Limiting
  - AdaptiveRateLimitPolicy sealed class: CLOSED(20/min) / HALF_OPEN(5/min) / OPEN(1/min)
  - AdaptiveRateLimitService: CircuitBreaker.EventPublisher 구독 → ConcurrentHashMap 정책 동적 교체
  - CircuitBreakerEventListenerConfig: ApplicationReadyEvent 시 모든 CB에 리스너 자동 등록
  - GET /api/adaptive-rate-limit/status: CB별 상태·정책·실패율 모니터링 API
  - 프론트엔드: /admin/adaptive-rate-limit 대시보드 (3초 polling, CB 상태 카드)
- Idempotency Key — Redis 기반 중복 요청 차단 (@Idempotent AOP)
- 필드 레벨 암호화 + 키 로테이션 (Google Tink AES-256-GCM)
  - TinkConfig: 키셋을 Redis에 저장, 앱 기동 시 자동 생성/로딩
  - EncryptedStringConverter: JPA AttributeConverter로 투명한 암복호화
  - Resume.content, User.email, User.mfaSecret 필드 암호화 적용
  - KeyRotationScheduler: 매일 새벽 2시 자동 로테이션 + 수동 로테이션 API
  - key_rotation_history 테이블로 로테이션 이력 추적
  - GET /api/encryption/status, POST /api/encryption/rotate, POST /api/encryption/verify
  - 프론트엔드: /admin/encryption 대시보드 (상태 카드, 검증 도구, 로테이션 히스토리)

## 데이터 / 검색
- Testcontainers MariaDB 마이그레이션 자동 검증 (testcontainers:mariadb:1.19.7)
  - MariaDbMigrationTest: V1~V7 테이블 구조·FK·인덱스·데이터 삽입 검증 (Docker 없는 환경 자동 skip)
  - MigrationStatusController: /api/migration/status Flyway 이력 조회 API
  - /admin/migration 대시보드: 5s polling, 버전·상태·실행시간 테이블
  - V5/V6 SQL H2 호환 수정: 인라인 INDEX → CREATE INDEX
- MariaDB + JPA/Hibernate + Flyway 스키마 관리
- QueryDSL 동적 쿼리 (이력서 복합 검색)
- Elasticsearch 이력서 전문 검색 (한국어 초성 지원)
- GraphQL API (Spring for GraphQL + DataLoader N+1 방지)
- JSON Schema 계약 검증 (networknt/json-schema-validator + @ValidateJsonSchema AOP)

## 캐시
- 2레벨 캐시: L1 Caffeine(JVM, 30s) + L2 Redisson(Redis, 300s) + pub/sub 무효화

## 이벤트 소싱
- 도메인 이벤트 append-only 감사 추적 (JPA EventStore)
  - DomainEvent JPA 엔티티 + Flyway V11 domain_events 테이블 (aggregate_type/aggregate_id/event_type/event_payload/actor/occurred_at)
  - DomainEventStore 포트 인터페이스 + JpaDomainEventStore 어댑터
  - EventSourcingService: record/replayAggregate/periodEvents/stats
  - @RecordEvent AOP 어노테이션: SpEL 기반 aggregateId/actor 자동 추출
  - GET /api/event-sourcing/stats, /recent, /aggregate/{type}/{id}, /aggregate/{type}/period
  - POST /api/event-sourcing/record 수동 이벤트 기록 API
  - 프론트엔드: /admin/event-sourcing 대시보드 (5s polling, 집계 리플레이, payload 펼치기)

## 비동기 / 이벤트
- Spring Modulith 이벤트 퍼블리케이션 (@ApplicationModuleListener + JPA outbox)
- RabbitMQ 감사 로그 파이프라인 (AuditLogAspect → RabbitMQ → ES 인덱싱)
- Webhook 발송 시스템 — OkHttp3 HMAC 서명, 지수 백오프 재시도, 발송 로그
- GraphQL Subscription 실시간 알림 (graphql-transport-ws 프로토콜)
  - ApplicationSubscriptionService: Sinks.many().multicast() 기반 Flux 스트림, 유저별 필터링
  - @SubscriptionMapping ApplicationSubscriptionController: 인증된 사용자 전용 스트림
  - schema.graphqls Subscription 타입 + ApplicationStatusChangedEvent 추가
  - JobApplicationService.changeStatus() 에서 이벤트 발행 (STOMP/Webhook과 병렬)
  - reactor-test StepVerifier 단위 테스트 3개
  - 프론트엔드: /admin/graphql-subscription 대시보드 (WebSocket 연결 로그, 실시간 이벤트 스트림)

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

## 멀티 테넌시
- 스키마 기반 멀티 테넌트 데이터 격리
  - TenantContext (ThreadLocal) + TenantFilter: X-Tenant-ID 헤더로 테넌트 식별, 미지정 시 default 테넌트
  - TenantRoutingDataSource (AbstractRoutingDataSource): 테넌트별 DataSource 동적 라우팅
  - TenantStore 포트 인터페이스 + JpaTenantStore 어댑터 (Flyway V9 tenants 테이블)
  - TenantService: 테넌트 생성/정지/활성화/삭제 라이프사이클 + 스키마명 자동 생성
  - REST API: POST/GET /api/tenants, PUT /{id}/suspend, PUT /{id}/activate, DELETE /{id}, GET /stats
  - 프론트엔드: /admin/tenant 관리 대시보드 (5s polling, 상태별 통계 카드, 테넌트 CRUD)

## 기타 인프라
- Spring Modulith 모듈 경계 검증 (ArchUnit)
- OpenAPI/Swagger UI
- WebSocket STOMP 실시간 알림
- Feature Flags (Unleash)
- Thymeleaf 이메일 템플릿
