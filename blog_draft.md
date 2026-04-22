[Fullstack] 분산 추적 Baggage 전파 - userId/tenantId가 RabbitMQ와 @Async 경계를 넘어가게 하기

---

traceId는 잘 전파되고 있었다. Zipkin 대시보드를 보면 HTTP 요청부터 RabbitMQ 메시지까지 깔끔하게 연결된다. 그런데 로그를 보다가 이상한 걸 발견했다. `AuditLogAspect`에서는 분명히 `userId=user-42`가 찍히는데, RabbitMQ 컨슈머 쪽 로그에는 userId가 없다. `@Async`로 처리되는 이메일 발송 로그에도 마찬가지다.

traceId/spanId는 Brave가 자동으로 전파해준다. 하지만 "이 요청을 누가, 어느 테넌트로 보냈는가"라는 비즈니스 컨텍스트는 전혀 전파되지 않는다. 스레드가 바뀌거나 메시지 브로커를 넘어가는 순간 컨텍스트가 사라진다.

W3C Baggage 스펙이 이 문제를 위해 존재한다. traceId처럼 요청 범위 내의 key-value 쌍을 HTTP 헤더나 메시지 헤더로 전파하는 표준이다. Micrometer Tracing이 이미 `BaggageManager` API를 제공하고 있었고, Spring Boot 3.2 + micrometer-tracing-bridge-brave 1.2.0이 이미 의존성에 있었다. 새 라이브러리를 추가할 필요가 없었다.

먼저 `application.yml`에 baggage 필드를 선언했다.

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    baggage:
      remote-fields:
        - userId
        - tenantId
      correlation:
        fields:
          - userId
          - tenantId
```

`remote-fields`는 HTTP 헤더로 자동 전파되고, `correlation.fields`는 MDC에 자동으로 올라간다. 로그 패턴도 같이 수정했다.

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{userId:-},%X{tenantId:-}]"
```

이제 `BaggageContextHolder`를 만들었다. Micrometer의 `Tracer`가 `BaggageManager`를 extends하기 때문에 `Tracer`만 주입받으면 된다.

```kotlin
@Component
class BaggageContextHolder(private val tracer: Tracer) {

    fun set(userId: String?, tenantId: String?) {
        userId?.let { tracer.createBaggage(USER_ID_KEY, it).makeCurrent(it) }
        tenantId?.let { tracer.createBaggage(TENANT_ID_KEY, it).makeCurrent(it) }
    }

    fun get(): BaggageContext {
        return BaggageContext(
            userId = tracer.getBaggage(USER_ID_KEY)?.get(),
            tenantId = tracer.getBaggage(TENANT_ID_KEY)?.get()
        )
    }

    companion object {
        const val USER_ID_KEY = "userId"
        const val TENANT_ID_KEY = "tenantId"
    }
}
```

JAR에서 직접 확인했을 때 `createBaggage(name, value)`가 deprecated 경고를 냈다. 대신 `createBaggage(name)`을 먼저 호출하고 `set(value).makeCurrent()`를 체이닝하는 방식이 권장되는데, 실제로는 `makeCurrent(value)` 오버로드가 있어서 한 줄로 쓸 수 있었다. 문서와 실제 JAR API가 미묘하게 다른 케이스다.

RabbitMQ 전파는 `MessagePostProcessor`로 처리했다. `AuditLogProducer`에서 `convertAndSend` 4번째 인자로 넘긴다.

```kotlin
@Component
class BaggageMessagePostProcessor(
    private val baggageContextHolder: BaggageContextHolder
) : MessagePostProcessor {

    override fun postProcessMessage(message: Message): Message {
        val ctx = baggageContextHolder.get()
        ctx.userId?.let { message.messageProperties.setHeader(USER_ID_KEY, it) }
        ctx.tenantId?.let { message.messageProperties.setHeader(TENANT_ID_KEY, it) }
        return message
    }
}
```

```kotlin
// AuditLogProducer
rabbitTemplate.convertAndSend(
    RabbitMqConfig.AUDIT_EXCHANGE,
    RabbitMqConfig.AUDIT_ROUTING_KEY,
    message,
    baggageMessagePostProcessor  // 추가된 부분
)
```

`@Async` 경계는 `TaskDecorator`로 처리했다. 기존 `AsyncConfig`에 MDC만 복사하던 인라인 람다를 `BaggageTaskDecorator`로 교체했다.

```kotlin
class BaggageTaskDecorator(private val tracer: Tracer) : TaskDecorator {

    override fun decorate(runnable: Runnable): Runnable {
        val userId = tracer.getBaggage(USER_ID_KEY)?.get()
        val tenantId = tracer.getBaggage(TENANT_ID_KEY)?.get()
        val mdcContext = MDC.getCopyOfContextMap()

        return Runnable {
            try {
                if (mdcContext != null) MDC.setContextMap(mdcContext)
                userId?.let { tracer.createBaggage(USER_ID_KEY, it).makeCurrent(it) }
                tenantId?.let { tracer.createBaggage(TENANT_ID_KEY, it).makeCurrent(it) }
                runnable.run()
            } finally {
                MDC.clear()
            }
        }
    }
}
```

핵심은 `decorate()` 호출 시점(메인 스레드)에서 Baggage 값을 캡처하고, `Runnable.run()` 시점(워커 스레드)에서 복원하는 것이다. 스레드 로컬 기반인 Micrometer Baggage는 스레드가 바뀌면 자동으로 사라지기 때문에 수동으로 재설정해야 한다.

테스트에서 문제가 있었다. `Tracer` 인터페이스의 추상 메서드가 예상보다 많았다. `withSpan`, `startScopedSpan`, `traceContextBuilder`까지 전부 구현해야 했다. Mockito로 mock하면 어노테이션 인터페이스 mock 시 Kotlin에서 NPE가 터지는 기존 문제가 있어서 `FakeTracer`를 직접 구현했다.

```kotlin
class FakeTracer : Tracer {
    private val store = mutableMapOf<String, String>()

    override fun createBaggage(name: String, value: String): Baggage =
        FakeBaggage(name, value, store).also { store[name] = value }

    override fun getBaggage(name: String): Baggage? =
        FakeBaggage(name, store[name], store)

    override fun getAllBaggage(): Map<String, String> = store.toMap()
    // ... 나머지 NOOP 구현
}
```

`TracingConfigTest`에도 문제가 생겼다. `AsyncConfig`가 이제 `Tracer`를 생성자로 받는데, 테스트에 `@MockBean Tracer`가 없어서 컨텍스트 로딩이 실패했다. `@MockBean lateinit var tracer: Tracer` 한 줄 추가로 해결됐다.

전체적으로 보면 HTTP 범위에서는 Spring의 자동 설정(`remote-fields`)이 처리해주고, 비동기 경계에서는 `BaggageTaskDecorator`, 메시지 경계에서는 `BaggageMessagePostProcessor`가 각각 책임진다. 프론트엔드에는 `/admin/baggage` 페이지를 만들어서 현재 트레이스의 Baggage 값을 실시간으로 확인할 수 있게 했다.

이제 컨슈머 로그에서도 `[app,traceId,spanId,user-42,tenant-A]` 형태로 출력된다. 문제가 생겼을 때 "누가 보낸 요청인지"를 로그 한 줄로 바로 알 수 있게 됐다.
