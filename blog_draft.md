[Fullstack] 백프레셔 제어 비동기 파이프라인 - RabbitMQ 메시지 폭주를 Reactor Flux로 막다

---

감사 로그 파이프라인이 언제 터질지 모른다는 불안감이 있었다.

`@RabbitListener`가 메시지를 받으면 Elasticsearch에 건건이 동기 저장한다. 평소엔 문제없다. 그런데 트래픽이 몰려서 RabbitMQ에 메시지가 쌓이기 시작하면? Spring AMQP의 기본 concurrency는 스레드 풀로 돌아가는데, ES 응답이 느려지면 그 스레드들이 전부 I/O 대기 상태로 막힌다. 스레드 풀이 고갈되면 새 메시지 처리가 밀리고, 결국 RabbitMQ consumer가 멈추는 상황이 올 수 있다.

배치 저장도 없었다. 메시지 100개가 오면 ES에 100번 HTTP 요청을 보낸다. 비효율도 비효율이지만, ES가 잠깐 느려지는 순간 그 100개가 전부 블로킹 대기로 쌓인다.

해결 방향은 명확했다. `@RabbitListener`는 메시지를 받는 즉시 Reactor `Sink`에 emit만 하고 리턴한다. 실제 저장은 Flux 파이프라인이 비동기로 처리한다. 버퍼가 있으니 ES가 잠깐 느려져도 메시지가 버퍼에서 기다리고, 스레드 풀은 자유롭다.

---

핵심은 `ReactiveAuditPipeline`이다.

```kotlin
@Component
class ReactiveAuditPipeline(private val auditLogStore: AuditLogStore) {

    private val sink = Sinks.many().multicast().onBackpressureBuffer<AuditLogMessage>(1000)

    @PostConstruct
    fun start() {
        sink.asFlux()
            .onBackpressureBuffer(1000) { dropped ->
                droppedCount.incrementAndGet()
                log.warn("Audit message dropped due to backpressure: action={}", dropped.action)
            }
            .bufferTimeout(50, Duration.ofMillis(100))
            .filter { it.isNotEmpty() }
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                { batch -> processBatch(batch) },
                { err -> log.error("Audit pipeline error", err) }
            )
    }

    fun emit(message: AuditLogMessage) {
        val result = sink.tryEmitNext(message)
        if (result.isFailure) {
            droppedCount.incrementAndGet()
        }
    }
}
```

`Sinks.many().multicast().onBackpressureBuffer(1000)` — 이 Sink는 구독자가 없어도 최대 1000개를 버퍼에 보관한다. emit은 논블로킹이라 `@RabbitListener` 스레드가 즉시 리턴한다.

`bufferTimeout(50, Duration.ofMillis(100))` — 50개가 쌓이거나 100ms가 지나면 리스트로 묶어 내려보낸다. 이게 배치 저장의 트리거다.

`publishOn(Schedulers.boundedElastic())` — ES HTTP 호출처럼 블로킹 I/O가 있는 작업을 별도 스레드 풀에서 실행한다. 메인 이벤트 루프를 막지 않는다.

`AuditLogConsumer`는 이제 이렇게 단순해졌다.

```kotlin
@RabbitListener(queues = [RabbitMqConfig.AUDIT_QUEUE])
fun receiveAuditLog(message: AuditLogMessage) {
    pipeline.emit(message)
}
```

받자마자 Sink에 던지고 끝이다. ES 응답 속도와 무관하게 스레드가 바로 풀린다.

---

데이터 레이어는 포트 인터페이스로 분리했다.

```kotlin
interface AuditLogStore {
    fun saveAll(documents: List<AuditLogDocument>)
}

@Component
class JpaAuditLogStore(private val repository: AuditLogRepository) : AuditLogStore {
    override fun saveAll(documents: List<AuditLogDocument>) {
        repository.saveAll(documents)
    }
}
```

서비스가 `ElasticsearchRepository`를 직접 알 필요가 없다. 테스트에서 `FakeAuditLogStore`를 10줄로 만들어 쓸 수 있다.

```kotlin
class FakeAuditLogStore : AuditLogStore {
    val saved = mutableListOf<AuditLogDocument>()
    var shouldFail = false

    override fun saveAll(documents: List<AuditLogDocument>) {
        if (shouldFail) throw RuntimeException("Simulated ES failure")
        saved.addAll(documents)
    }
}
```

`shouldFail` 플래그로 ES 장애 시나리오도 테스트한다. 파이프라인이 예외를 잡아서 로그만 남기고 계속 살아있는지 확인하는 게 중요했다.

---

구현 중에 한 가지 확인이 필요했다. `spring-boot-starter-webflux`를 추가하면 WebMVC가 WebFlux로 교체되는 게 아닌지였다.

결론은 안전하다. `spring-boot-starter-web`이 클래스패스에 있으면 `spring.main.web-application-type`이 `servlet`으로 고정된다. WebFlux가 함께 있어도 MVC 모드를 유지한다. Reactor는 파이프라인 내부용으로만 쓰이고, 기존 컨트롤러나 필터 체인에 영향을 주지 않는다.

사실 `reactor-core`는 이미 transitive 의존성으로 들어와 있었다. GraphQL Subscription 때문에 `spring-boot-starter-graphql`이 `spring-webflux`를 끌어오고 있었다. webflux starter를 명시 추가한 건 Spring Boot의 자동 설정을 제대로 활성화하기 위해서였다.

---

테스트는 시간 기반이라 약간 주의가 필요하다.

```kotlin
@Test
fun `emit single message is saved via store`() {
    val msg = buildMessage("CREATE_RESUME", "SUCCESS")
    pipeline.emit(msg)
    Thread.sleep(300)
    assertEquals(1, store.saved.size)
}
```

`bufferTimeout`이 100ms이니 300ms 정도 기다리면 배치가 플러시된다. CI 환경에서 타이밍이 빡빡하면 실패할 수도 있는 구조인데, Reactor의 `StepVerifier`와 `VirtualTimeScheduler`를 쓰면 가상 시간으로 테스트할 수 있다. 지금은 단순하게 `Thread.sleep`으로 처리했다.

파이프라인 통계는 `/api/audit/pipeline/stats`로 조회할 수 있다.

```json
{
  "processed": 1247,
  "dropped": 0
}
```

dropped가 0이면 버퍼가 충분한 것이고, 올라가기 시작하면 버퍼 크기나 ES 처리 속도를 점검해야 한다는 신호다.

---

프론트엔드 `/admin/pipeline` 페이지에서 처리 건수, 드롭 건수, 드롭률을 5초 간격으로 모니터링할 수 있다. 파이프라인 구조도도 한눈에 보이게 배치했다. 운영 중에 드롭률이 1%를 넘으면 색이 빨갛게 바뀐다.

지금 구조에서 개선할 여지가 있다면 드롭된 메시지의 DLQ 연동이다. 현재는 드롭 카운트만 올라가고 해당 메시지는 사라진다. 버퍼가 꽉 찰 정도의 폭주라면 그 메시지들도 기록이 필요한데, 드롭 핸들러에서 DLQ로 라우팅하는 것이 다음 단계다.
