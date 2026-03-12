# Spring Modulith: 분산 시스템의 아킬레스건, '이벤트 유실' 완벽 방어하기

안녕하세요. 오늘은 MSA(Microservices Architecture)로 가기 전, 가장 견고한 단계인 **Modular Monolith**를 완성하는 핵심 기술에 대해 이야기해보려 합니다. 바로 **Spring Modulith**와 **Event Publication Registry**입니다.

## 1. 이벤트 기반 시스템의 치명적인 함정

우리는 보통 비즈니스 로직이 끝난 후 이벤트를 발행(`publishEvent`)합니다. 하지만 이런 시나리오를 생각해 보셨나요?

1. 비즈니스 로직(DB 저장) 성공.
2. 이벤트 발행 시도.
3. **하지만 메시지 브로커(RabbitMQ, Kafka)가 일시적 장애?**
4. 서비스 응답은 성공으로 나가지만, 후속 처리(알림, 로그)는 유실됩니다.

이것이 바로 분산 시스템에서 발생하는 **Data Inconsistency**의 시작입니다.

## 2. 해결책: Spring Modulith Event Publication Registry

Spring Modulith는 이를 해결하기 위해 **Transactional Event Publication** 패턴을 기본으로 제공합니다.

- **원자적 저장**: 비즈니스 로직의 트랜잭션 내에 '발행해야 할 이벤트 정보'를 DB(`EVENT_PUBLICATION` 테이블)에 함께 저장합니다.
- **리스너 성공 확인**: 이벤트 리스너가 성공적으로 실행되어야만 해당 로그를 '완료' 처리합니다.
- **자동 복구**: 실행되지 못한 이벤트는 백그라운드 스케줄러가 브로커가 정상화될 때까지 무한 재시도합니다.

이번 미션에서는 `AuditLogProducer`의 예외 처리를 정교화하여, 브로커 장애 시 Modulith가 이를 정확히 감지하고 복구 프로세스를 가동하도록 개선했습니다.

## 3. 프리미엄 이벤트 대시보드: "보이지 않는 것을 보게 하다"

인프라의 복구 메커니즘이 아무리 훌륭해도, 관리자가 모르면 불안합니다. 그래서 **Event Publication Monitoring Dashboard**를 직접 구축했습니다.

- **실시간 추적**: 현재 발행 대기 중인(Incomplete) 이벤트와 완료된 이벤트를 한눈에 확인.
- **수동 개입**: 스케줄러를 기다리지 않고 관리자가 즉시 재발행(Manual Retry) 요청 가능.
- **아키텍처 가드레일**: 모듈 간 불필요한 의존성을 ArchUnit으로 강제하여 '건강한 결합도'를 유지합니다.

## 4. 진정한 'Resilient System'으로의 도약

Resilience4j(Mission 2)로 서비스 장애를 방어하고, Spring Modulith(Mission 4)로 데이터 유실을 막았습니다. 이제 우리의 시스템은 어떤 외부 환경 변화에도 비즈니스 일관성을 잃지 않는 강력한 내성을 갖추게 되었습니다.

아키텍처의 견고함은 화려한 UI만큼이나 중요합니다. 여러분의 프로젝트도 '유실 없는 이벤트'로 채워보시길 바랍니다.

---
**GitHub**: [qjawns0222/fullstackupgrade](https://github.com/qjawns0222/fullstackupgrade)
**Mission Status**: COMPLETE (Event-Driven Modularity)
