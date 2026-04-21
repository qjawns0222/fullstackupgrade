[Fullstack] 이벤트 소싱 감사 추적 - 도메인 이벤트를 append-only로 저장하고 시점 재현하기

---

감사 로그는 이미 있었다. RabbitMQ → Elasticsearch로 흘러가는 파이프라인도 있고, AuditLogAspect가 메서드마다 현재 상태를 찍어주고 있었다. 그런데 어느 날 "이 이력서가 사흘 전 상태로 어떻게 생겼었는지 볼 수 있나요?"라는 질문을 받고 막혔다. 현재 상태는 알 수 있지만, 특정 시점의 상태를 재현하는 건 불가능했다.

이게 이벤트 소싱을 도입하고 싶었던 이유다. 상태를 덮어쓰는 게 아니라 "무슨 일이 일어났는가"를 순서대로 쌓는 것. 그러면 임의의 시점까지 이벤트를 재생해서 그 시점 상태를 복원할 수 있다.

Axon Framework도 검토했다. 근데 프로젝트 규모에 비해 너무 무겁다. EventStore, EventBus, CommandBus, Aggregate 어노테이션... 기존 JPA 코드를 대거 바꿔야 한다. 우리가 필요한 건 이벤트를 시계열로 저장하고 조회하는 것 뿐이다. Flyway 마이그레이션 하나와 JPA 엔티티 하나로 시작했다.

```sql
CREATE TABLE domain_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    event_payload  TEXT NOT NULL,
    actor          VARCHAR(100),
    occurred_at    DATETIME(6) NOT NULL
);
CREATE INDEX idx_domain_events_aggregate ON domain_events (aggregate_type, aggregate_id);
```

`aggregate_type`은 "Resume", "JobApplication" 같은 도메인 개념이고, `aggregate_id`는 해당 엔티티의 PK다. `event_payload`는 JSON 문자열로 자유롭게 담는다. 스키마를 강제하지 않는 대신 유연성을 얻었다.

포트 인터페이스는 얇게 유지했다.

```kotlin
interface DomainEventStore {
    fun append(event: DomainEvent): DomainEvent
    fun findByAggregate(aggregateType: String, aggregateId: String): List<DomainEvent>
    fun findRecent(limit: Int): List<DomainEvent>
    fun findByAggregateTypeAndPeriod(aggregateType: String, from: LocalDateTime, to: LocalDateTime): List<DomainEvent>
    fun stats(): DomainEventStats
}
```

서비스가 JpaRepository를 직접 알 필요가 없다. JpaDomainEventStore가 이 인터페이스를 구현하고, 테스트에서는 FakeDomainEventStore가 인메모리 리스트로 대체한다.

AOP로 `@RecordEvent` 어노테이션도 만들었다. SpEL로 aggregateId와 actor를 메서드 파라미터에서 추출한다.

```kotlin
@Around("@annotation(recordEvent)")
fun around(pjp: ProceedingJoinPoint, recordEvent: RecordEvent): Any? {
    val result = pjp.proceed()

    val sig = pjp.signature as MethodSignature
    val ctx = StandardEvaluationContext().apply {
        sig.parameterNames.forEachIndexed { i, name -> setVariable(name, pjp.args[i]) }
        setVariable("result", result)
    }

    val aggregateId = parser.parseExpression(recordEvent.aggregateIdSpel).getValue(ctx)?.toString() ?: "unknown"
    store.append(DomainEvent(
        aggregateType = recordEvent.aggregateType,
        aggregateId   = aggregateId,
        eventType     = recordEvent.eventType,
        eventPayload  = objectMapper.writeValueAsString(result ?: emptyMap<String, Any>()),
        actor         = ...
    ))
    return result
}
```

사용하는 쪽에서는 이렇게 된다.

```kotlin
@RecordEvent(
    aggregateType = "Resume",
    eventType = "RESUME_UPDATED",
    aggregateIdSpel = "#resumeId",
    actorSpel = "#actor"
)
fun updateResume(resumeId: Long, actor: String, dto: ResumeUpdateDto): Resume { ... }
```

구현 중 한 가지 실수가 있었다. `DomainEvent`를 일반 class로 선언했는데, 테스트에서 포지셔널 생성자 호출로 인스턴스를 만들다 보니 `occurredAt` 파라미터 위치에서 타입 불일치 컴파일 에러가 났다. `LocalDateTime`이 들어가야 할 자리에 `String?`이 추론되는 상황이었다. named parameter로 바꾸니 바로 해결됐다. 코드가 길어지는 단점이 있지만 생성자 파라미터 순서에 의존하는 건 더 위험하다.

테스트는 FakeDomainEventStore 하나로 다섯 가지 시나리오를 커버했다.

```kotlin
@Test
fun `periodEvents filters by time range`() {
    val now = LocalDateTime.now()
    store.appendWithTime(DomainEvent(aggregateType = "Resume", aggregateId = "1",
        eventType = "OLD", eventPayload = "{}", actor = null, occurredAt = now.minusDays(2)))
    store.appendWithTime(DomainEvent(aggregateType = "Resume", aggregateId = "1",
        eventType = "NEW", eventPayload = "{}", actor = null, occurredAt = now))

    val events = service.periodEvents("Resume", now.minusHours(1), now.plusHours(1))
    assertEquals(1, events.size)
    assertEquals("NEW", events[0].eventType)
}
```

Fake 구현은 10줄 남짓이다. Mockito stub 없이도 시간 필터 로직을 정확하게 검증할 수 있다.

프론트엔드는 `/admin/event-sourcing`에 추가했다. 5초 polling으로 최근 이벤트 30건을 보여주고, aggregate type + id를 입력하면 해당 집계의 전체 이벤트 히스토리를 시계열로 표시한다. 이벤트 행을 클릭하면 payload JSON이 펼쳐진다.

이 구조의 진짜 장점은 나중에 생긴다. 지금은 단순 조회만 하지만, 이벤트 스트림이 쌓이면 "특정 시점 상태 재현"이 가능해진다. Resume 이벤트를 CREATED부터 특정 날짜까지만 재생하면 그 시점의 이력서를 복원할 수 있다. 이건 현재 상태 기반 감사 로그로는 절대 할 수 없는 것이다.

한 가지 숙제가 남았다. 현재 event_payload가 TEXT 컬럼에 JSON 문자열로 들어간다. 나중에 특정 필드로 쿼리하려면 MariaDB JSON 함수를 쓰거나 Elasticsearch에 인덱싱해야 한다. 지금은 단순 저장과 재생만 하니 일단 이 상태로 두기로 했다.
