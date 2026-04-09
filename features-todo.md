# 구현 예정 기능 목록

이 파일은 aiblog-agent 스킬이 자동 관리한다.
- 스킬 실행 시 맨 위 항목을 가져와 구현
- 구현 완료 후 해당 항목 삭제
- 목록이 비면 코드베이스 재스캔 후 10개 새로 생성

## 생성일: 2026-04-02

---

3. **OpenTelemetry 메서드 레벨 타이밍 추적**
   - 문제: Micrometer로는 엔드포인트 내부 각 처리 단계 시간 추적 불가 — 성능 병목 파악 어려움
   - 해결: OpenTelemetry SDK + @WithSpan 커스텀 어노테이션으로 메서드 레벨 추적, 병목 자동 감지 대시보드
   - 신규 라이브러리: io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.1.0

4. **멀티 테넌트 데이터 격리 (스키마 기반)**
   - 문제: 단일 스키마로 사용자/회사별 데이터 격리 없어 확장 시 데이터 유출 위험
   - 해결: Hibernate 멀티테넌시 + 동적 DataSource 라우팅으로 테넌트별 독립 스키마 적용
   - 신규 라이브러리: org.hibernate.orm:hibernate-core:6.4.4.Final (멀티테넌시 설정 강화)

5. **사용자 행동 퍼널 분석**
   - 문제: 감사 로그는 있으나 조회→저장→다운로드 퍼널 분석 없어 전환율/리텐션 파악 불가
   - 해결: 백엔드 이벤트 컬렉터 + Elasticsearch 집계 쿼리로 행동 퍼널 수집, 프론트엔드 Funnel 시각화 대시보드
   - 신규 라이브러리: com.amplitude:java-sdk:1.10.2

6. **GraphQL 구독(Subscription) 실시간 알림**
   - 문제: WebSocket STOMP 알림은 있으나 GraphQL 레이어에서 실시간 데이터 스트리밍 불가 — GraphQL 클라이언트 호환성 문제
   - 해결: Spring for GraphQL WebSocket Subscription + graphql-transport-ws 프로토콜로 GraphQL 네이티브 실시간 스트리밍
   - 신규 라이브러리: org.springframework.boot:spring-boot-starter-graphql (이미 있음) + graphql-ws 프로토콜 설정

7. **Circuit Breaker 상태 기반 적응형 Rate Limiting**
   - 문제: 외부 서비스(ES, Redis) 장애 시 Rate Limit은 그대로 유지 — 장애 전파 가속화
   - 해결: Resilience4j CircuitBreaker 상태 변화 이벤트를 구독해 Bucket4j Rate Limit 정책을 동적으로 완화/강화
   - 신규 라이브러리: io.github.resilience4j:resilience4j-circuitbreaker:2.2.0 (이미 있음) + 이벤트 기반 동적 정책 Bean
