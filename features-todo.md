# 구현 예정 기능 목록

이 파일은 aiblog-agent 스킬이 자동 관리한다.
- 스킬 실행 시 맨 위 항목을 가져와 구현
- 구현 완료 후 해당 항목 삭제
- 목록이 비면 코드베이스 재스캔 후 10개 새로 생성

## 생성일: 2026-04-02

---

1. **GraphQL 구독(Subscription) 실시간 알림**
   - 문제: WebSocket STOMP 알림은 있으나 GraphQL 레이어에서 실시간 데이터 스트리밍 불가 — GraphQL 클라이언트 호환성 문제
   - 해결: Spring for GraphQL WebSocket Subscription + graphql-transport-ws 프로토콜로 GraphQL 네이티브 실시간 스트리밍
   - 신규 라이브러리: org.springframework.boot:spring-boot-starter-graphql (이미 있음) + graphql-ws 프로토콜 설정

2. **Circuit Breaker 상태 기반 적응형 Rate Limiting**
   - 문제: 외부 서비스(ES, Redis) 장애 시 Rate Limit은 그대로 유지 — 장애 전파 가속화
   - 해결: Resilience4j CircuitBreaker 상태 변화 이벤트를 구독해 Bucket4j Rate Limit 정책을 동적으로 완화/강화
   - 신규 라이브러리: io.github.resilience4j:resilience4j-circuitbreaker:2.2.0 (이미 있음) + 이벤트 기반 동적 정책 Bean
