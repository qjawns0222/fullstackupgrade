[Fullstack] GraphQL Subscription 실시간 알림 - graphql-transport-ws로 폴링 없애기

---

WebSocket STOMP 알림은 이미 있었다. 분석 진행 상태, 배치 잡 완료, Redis pub/sub 메시지까지 `/topic/notifications`로 잘 날아오고 있었다. 그런데 GraphQL 레이어에서는 아무것도 없었다. 클라이언트가 지원서 상태 변경을 알려면 5초마다 `myApplications` 쿼리를 폴링하거나, STOMP 연결을 별도로 유지해야 했다.

GraphQL 클라이언트를 쓰는 입장에서 이건 어색하다. GraphQL을 선택한 이유 중 하나가 "단일 인터페이스"인데, 폴링이나 STOMP를 따로 붙이면 그 이점이 반감된다. GraphQL Subscription을 도입해서 상태 변경을 GraphQL 프로토콜로 스트리밍하기로 했다.

---

Spring for GraphQL 1.2.4가 이미 의존성에 있었다. `@SubscriptionMapping` 어노테이션도 있고, Flux를 반환하면 된다는 건 알고 있었다. 문제는 전송 계층이었다.

WebSocket을 활성화하는 방법이 두 가지다. `graphql-ws` 라이브러리가 쓰는 `graphql-transport-ws` 프로토콜과, 구버전 `subscriptions-transport-ws` 프로토콜. Spring for GraphQL은 `graphql-transport-ws`를 기본으로 지원한다. `application.yml`에 경로만 추가하면 된다.

```yaml
spring:
  graphql:
    websocket:
      path: /graphql-ws
```

별도 라이브러리 추가 없이 Spring Boot Starter WebSocket이 이미 있으니까 그걸로 충분했다.

---

이벤트를 어디 두느냐가 구조 문제였다. 처음에는 `com.example.demo.graphql.subscription` 패키지에 `ApplicationSubscriptionService`를 뒀다. 그랬더니 ArchUnit `noCyclicDependencies` 테스트가 터졌다.

```
Cycle detected: Slice graphql → Slice service → Slice graphql
```

`JobApplicationService`(service 슬라이스)가 `ApplicationSubscriptionService`(graphql 슬라이스)를 의존하고, `graphql` 슬라이스는 `service` 슬라이스를 의존하니까 순환이다. 해결책은 단순했다. `ApplicationSubscriptionService`와 이벤트 클래스를 `com.example.demo.notification` 패키지로 빼는 것. `service`가 `notification`을 참조하고, `graphql`도 `notification`을 참조하면 순환이 없다.

```kotlin
// com.example.demo.notification
@Service
class ApplicationSubscriptionService {

    private val sink: Sinks.Many<ApplicationStatusChangedEvent> =
        Sinks.many().multicast().onBackpressureBuffer()

    fun publish(event: ApplicationStatusChangedEvent) {
        sink.tryEmitNext(event)
    }

    fun statusChangesForUser(userId: Long): Flux<ApplicationStatusChangedEvent> =
        sink.asFlux().filter { it.userId == userId }
}
```

`Sinks.many().multicast().onBackpressureBuffer()`를 선택한 이유가 있다. `unicast()`는 구독자가 하나여야 하고, `replay()`는 과거 이벤트를 새 구독자에게 재전송한다. 실시간 알림은 현재 연결된 사용자에게만 그 시점 이후 이벤트를 보내면 되니까 `multicast()`가 맞다.

---

Subscription 컨트롤러는 간단하다.

```kotlin
@Controller
class ApplicationSubscriptionController(
    private val subscriptionService: ApplicationSubscriptionService,
    private val userRepository: UserRepository
) {

    @SubscriptionMapping
    fun applicationStatusChanged(
        @AuthenticationPrincipal userDetails: UserDetails
    ): Flux<ApplicationStatusChangedEvent> {
        val user = userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
        return subscriptionService.statusChangesForUser(user.id!!)
    }
}
```

`statusChangesForUser(userId)`가 Flux를 필터링해서 반환한다. 연결한 사용자의 이벤트만 흘러간다. 다른 사용자의 상태 변경은 필터에서 걸린다.

스키마는 `Subscription` 타입을 추가했다.

```graphql
type Subscription {
    applicationStatusChanged: ApplicationStatusChangedEvent!
}

type ApplicationStatusChangedEvent {
    applicationId: ID!
    companyName: String!
    position: String!
    newStatus: JobApplicationStatus!
    userId: ID!
    timestamp: Long!
}
```

이벤트 발행은 `JobApplicationService.changeStatus()` 끝에 붙였다. 이미 webhook 발송이 있었는데 그 바로 다음에 추가했다.

```kotlin
// 7. Publish GraphQL Subscription event
subscriptionService.publish(
    ApplicationStatusChangedEvent(
        applicationId = savedApplication.id!!,
        companyName = savedApplication.companyName,
        position = savedApplication.position,
        newStatus = savedApplication.status,
        userId = userId
    )
)
```

STOMP, webhook, Subscription 세 경로로 동시에 나간다. 클라이언트가 어떤 방식으로 연결하든 알림을 받을 수 있다.

---

테스트는 `StepVerifier`로 작성했다. `reactor-test` 의존성이 없었다.

```groovy
testImplementation 'io.projectreactor:reactor-test'
```

추가하고 나서 세 가지를 검증했다.

```kotlin
@Test
fun `statusChanges emits published events`() {
    val event = ApplicationStatusChangedEvent(
        applicationId = 1L, companyName = "TestCorp", position = "Backend Dev",
        newStatus = JobApplicationStatus.INTERVIEW, userId = 42L
    )

    val flux = service.statusChanges()

    StepVerifier.create(flux.take(1))
        .then { service.publish(event) }
        .expectNextMatches { e -> e.applicationId == 1L && e.companyName == "TestCorp" }
        .verifyComplete()
}

@Test
fun `statusChangesForUser filters events by userId`() {
    // userId 99의 이벤트는 통과하지 않고, 42의 이벤트만 도달한다
}

@Test
fun `statusChangesForUser does not emit events for other users`() {
    // 200ms timeout으로 다른 유저 이벤트가 절대 안 온다는 걸 검증
}
```

마지막 테스트가 포인트다. 필터가 제대로 동작하는지는 "이벤트가 오지 않는다"를 검증해야 한다. `timeout(Duration.ofMillis(300))`으로 TimeoutException을 기대했다.

---

`JobApplicationServiceTest`도 하나 고쳤다. `@InjectMocks`가 생성자 파라미터를 전부 주입하려다가 `subscriptionService`가 없어서 NPE가 났다. `@Mock`만 추가하면 됐다.

```kotlin
@Mock private lateinit var subscriptionService: ApplicationSubscriptionService
```

---

실제로 WebSocket 연결이 어떻게 동작하는지 보려고 프론트엔드에 `/admin/graphql-subscription` 페이지도 만들었다. `graphql-transport-ws` 프로토콜 핸드셰이크 순서가 있다.

1. `connection_init` 전송 (Authorization 헤더 포함)
2. `connection_ack` 수신 확인
3. `subscribe` 메시지 전송
4. 서버에서 `next` 메시지로 이벤트 수신

이 순서가 맞아야 연결이 된다. `connection_ack` 없이 바로 `subscribe`를 보내면 서버가 무시한다.

```typescript
ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.type === 'connection_ack') {
        ws.send(JSON.stringify({
            id: '1',
            type: 'subscribe',
            payload: { query: SUBSCRIPTION_QUERY }
        }));
    } else if (msg.type === 'next') {
        const event = msg.payload?.data?.applicationStatusChanged;
        if (event) setEvents(prev => [event, ...prev.slice(0, 99)]);
    }
};
```

연결 로그를 화면에 표시해뒀다. 실제로 `changeApplicationStatus` GraphQL mutation을 날리면 0ms 지연 없이 이벤트가 수신된다. 폴링 5초 대기가 없다는 게 확실히 느껴진다.

---

이번 구현에서 배운 것 하나. ArchUnit 순환 의존 검사는 패키지 설계를 강제한다. 처음에 편한 위치에 파일을 뒀다가 테스트가 터졌고, 그게 오히려 `notification` 패키지를 독립적으로 분리하는 계기가 됐다. 테스트가 설계를 개선시킨 케이스다.
