# 구현 예정 기능 목록

이 파일은 aiblog-agent 스킬이 자동 관리한다.
- 스킬 실행 시 맨 위 항목을 가져와 구현
- 구현 완료 후 해당 항목 삭제
- 목록이 비면 코드베이스 재스캔 후 10개 새로 생성

## 생성일: 2026-04-21

---

1. **캐시 워밍 전략 (Cache Warming on Startup)**
   - 문제: 2레벨 캐시(Caffeine+Redis)가 콜드 스타트 시 비어있어 초기 트래픽에 DB 풀히트 발생
   - 해결: ApplicationReadyEvent 훅에서 자주 조회되는 데이터(resumeList, trendStats)를 미리 로딩하는 CacheWarmupService 구현, 워밍 진행상황 SSE 스트림 노출
   - 신규 라이브러리: 추가 없음 (기존 Caffeine + Redisson 활용), SSE는 Spring MVC SseEmitter

2. **실시간 알림 허브 (Notification Hub with Preferences)**
   - 문제: WebSocket STOMP, GraphQL Subscription, Webhook이 각각 독립적으로 존재하며 사용자별 채널 선호도 관리가 없음
   - 해결: NotificationChannel 열거형(STOMP/GRAPHQL/WEBHOOK/EMAIL) + UserNotificationPreference JPA 엔티티 — 단일 NotificationRouter가 선호도에 따라 채널 선택, /api/notifications/preferences CRUD API
   - 신규 라이브러리: 추가 없음 (기존 인프라 통합)

3. **쿼리 계획 캐싱 & 힌트 자동 주입 (Query Plan Cache + Hint Injection)**
   - 문제: SlowQueryListener가 느린 쿼리를 감지하고 EXPLAIN 분석까지 하지만, 동일 쿼리가 반복 느려져도 자동 힌트를 주입하지 않음
   - 해결: 동일 쿼리 패턴이 N회 이상 슬로우 감지되면 Hibernate QueryHint(USE INDEX) 또는 EntityManager 힌트를 동적으로 등록하는 QueryHintRegistry 구현
   - 신규 라이브러리: `net.ttddyy:datasource-proxy:1.10` (이미 있음)

4. **A/B 테스트 프레임워크 (A/B Testing with Unleash Variants)**
   - 문제: Unleash Feature Flags가 켜짐/꺼짐만 지원하고, 사용자 코호트별 변형(variant) 실험을 체계적으로 추적하지 않음
   - 해결: Unleash Variant API 활용, @ABTest AOP 어노테이션으로 메서드 분기 + UserEvent에 variant 필드 추가해 퍼널 분석과 연동
   - 신규 라이브러리: `io.getunleash:unleash-client-java:9.2.0` (이미 있음), variant API 활용

5. **백프레셔 제어 비동기 파이프라인 (Reactive Backpressure Pipeline)**
   - 문제: RabbitMQ 감사 로그 파이프라인이 동기 처리라 메시지 폭주 시 스레드 풀 고갈 위험
   - 해결: Spring WebFlux + Reactor 기반 비동기 파이프라인으로 감사 로그 처리 교체 — Flux.create + onBackpressureBuffer(1000), ES 배치 인덱싱(bufferTimeout 100ms/50건)
   - 신규 라이브러리: `org.springframework.boot:spring-boot-starter-webflux` (추가)

6. **시크릿 볼트 통합 (HashiCorp Vault Secret Management)**
   - 문제: application.yml에 DB 패스워드·Gmail 앱 비밀번호·JWT 시크릿이 평문으로 하드코딩되어 있음
   - 해결: Spring Cloud Vault 연동, bootstrap.yml로 Vault KV 엔진에서 시크릿 로딩 + 로컬 개발용 dotenv 폴백
   - 신규 라이브러리: `org.springframework.cloud:spring-cloud-starter-vault-config:4.1.1`

7. **분산 잡 오케스트레이션 (Distributed Job Orchestration with DAG)**
   - 문제: Spring Batch 주간 트렌드 분석 Job이 단일 스텝 선형 실행이라 스텝 간 의존성·병렬 실행·실패 스텝 재시도 전략이 없음
   - 해결: Spring Batch Flow DSL로 DAG 형태 Step 의존성 정의 — 병렬 파티셔닝(PartitionStep), 조건 분기(JobExecutionDecider), 실패 스텝 자동 재시작
   - 신규 라이브러리: `org.springframework.boot:spring-boot-starter-batch` (이미 있음), Batch Flow API 활용
