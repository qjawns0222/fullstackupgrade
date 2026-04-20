[Fullstack] Circuit Breaker 상태 기반 적응형 Rate Limiting - 장애 전파를 막는 동적 정책 교체

---

Rate Limiting은 보통 "얼마나 자주 호출할 수 있나"를 고정된 숫자로 표현한다. 분당 20회, 초당 5회. 그런데 이 숫자가 외부 서비스가 죽어가는 상황에서도 그대로 유지된다면 어떻게 될까?

Elasticsearch가 응답을 멈추고 Circuit Breaker가 OPEN 상태로 전환됐다. 그런데 Rate Limit은 여전히 분당 20회를 허용하고 있다. 트래픽은 계속 들어오고, 각 요청은 fallback을 타거나 타임아웃을 기다리며 쌓인다. CB가 장애를 감지했음에도 Rate Limit이 제동을 걸지 않으니 장애가 확산된다. 이게 내가 풀고 싶었던 문제였다.

Resilience4j의 CircuitBreaker가 이미 `s3Service`, `ocrService` 두 인스턴스에 붙어 있었다. 상태 변화 이벤트를 발행하는 `EventPublisher`도 있었다. Bucket4j는 이미 Redis ProxyManager로 분산 버킷을 운영 중이었다. 두 라이브러리를 연결하는 게 핵심이었다.

먼저 정책을 모델링했다.

```kotlin
sealed class AdaptiveRateLimitPolicy(
    val capacity: Long,
    val refillTokens: Long,
    val refillPeriod: Duration,
) {
    data object Closed   : AdaptiveRateLimitPolicy(20, 20, Duration.ofMinutes(1))
    data object HalfOpen : AdaptiveRateLimitPolicy(5,  5,  Duration.ofMinutes(1))
    data object Open     : AdaptiveRateLimitPolicy(1,  1,  Duration.ofMinutes(1))

    fun toBucketConfiguration(): BucketConfiguration =
        BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(refillTokens, refillPeriod)
                    .build()
            )
            .build()
}
```

CLOSED는 평상시 20/min, HALF_OPEN은 회복 탐색 중이니 5/min으로 조심하고, OPEN은 사실상 차단에 가까운 1/min. 숫자는 팀 상황에 따라 튜닝할 수 있지만, 방향성은 이게 맞다고 생각한다.

그 다음은 이벤트 구독이다. CB의 `getEventPublisher().onStateTransition()` 메서드로 상태 전환 이벤트를 받을 수 있다. 여기서 API를 실제로 JAR에서 확인했는데, `StateTransition` enum의 `toState` 필드가 Kotlin에서 `getToState()` → `.toState` 프로퍼티로 자동 변환된다는 걸 확인했다.

```kotlin
@Service
class AdaptiveRateLimitService(
    private val proxyManager: ProxyManager<ByteArray>,
) {
    private val currentPolicies = ConcurrentHashMap<String, AdaptiveRateLimitPolicy>()

    fun registerCircuitBreaker(cb: CircuitBreaker) {
        cb.eventPublisher.onStateTransition { event ->
            val newPolicy = when (event.stateTransition.toState) {
                CircuitBreaker.State.OPEN      -> AdaptiveRateLimitPolicy.Open
                CircuitBreaker.State.HALF_OPEN -> AdaptiveRateLimitPolicy.HalfOpen
                CircuitBreaker.State.CLOSED    -> AdaptiveRateLimitPolicy.Closed
                else -> return@onStateTransition
            }
            currentPolicies[cb.name] = newPolicy
            log.warn("[AdaptiveRateLimit] CB '{}' → {} : {} req/min",
                cb.name, event.stateTransition.toState, newPolicy.capacity)
        }
    }

    fun resolveBucket(cbName: String, bucketKey: String) =
        proxyManager.builder().build(
            bucketKey.toByteArray(Charsets.UTF_8),
            currentPolicyFor(cbName).toBucketConfiguration(),
        )
}
```

`ConcurrentHashMap`으로 CB 이름 → 현재 정책을 관리한다. 이벤트가 오면 맵을 교체하고, `resolveBucket` 호출 시 현재 맵에서 정책을 꺼내 `BucketConfiguration`을 생성한다. Bucket4j의 Redis ProxyManager는 `build(key, BucketConfiguration)` 오버로드를 지원하니 직접 전달하면 된다.

리스너 등록은 `ApplicationReadyEvent`에서 처리했다. `CircuitBreakerRegistry.getAllCircuitBreakers()`로 등록된 모든 CB를 가져와 일괄 등록한다.

```kotlin
@Component
class CircuitBreakerEventListenerConfig(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val adaptiveRateLimitService: AdaptiveRateLimitService,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun registerListeners() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { cb ->
            adaptiveRateLimitService.registerCircuitBreaker(cb)
        }
    }
}
```

테스트에서 한 가지 함정이 있었다. Mockito의 `@ExtendWith(MockitoExtension::class)`는 strict stubbing을 적용하는데, `@BeforeEach`에서 `proxyManager.builder()` stub을 잡아두면 해당 stub을 사용하지 않는 테스트에서 `UnnecessaryStubbingException`이 난다. 그래서 ProxyManager stub은 `resolveBucket`을 직접 검증하는 테스트 메서드 안으로 이동했다.

또 `bucketBuilder.build(any(), any<Supplier<BucketConfiguration>>())`로 stub하면 실제 코드가 `build(ByteArray, BucketConfiguration)` 오버로드를 호출할 때 타입이 안 맞아 `PotentialStubbingProblem`이 발생한다. `any(BucketConfiguration::class.java)`로 명시해야 정확히 매칭된다.

실제 CB 상태 전환 테스트는 `CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker(name)`으로 실제 CB 인스턴스를 만들고 `transitionToOpenState()`, `transitionToHalfOpenState()`, `transitionToClosedState()`를 직접 호출했다. 이렇게 하면 실제 이벤트가 발행되고 리스너가 트리거된다.

```kotlin
@Test
fun `CB가 OPEN → HALF_OPEN → CLOSED 순으로 전환되면 정책이 순서대로 바뀐다`() {
    val cb = createCircuitBreaker("testCb3")
    service.registerCircuitBreaker(cb)

    cb.transitionToOpenState()
    assertEquals(AdaptiveRateLimitPolicy.Open, service.currentPolicyFor("testCb3"))

    cb.transitionToHalfOpenState()
    assertEquals(AdaptiveRateLimitPolicy.HalfOpen, service.currentPolicyFor("testCb3"))

    cb.transitionToClosedState()
    assertEquals(AdaptiveRateLimitPolicy.Closed, service.currentPolicyFor("testCb3"))
}
```

모니터링 API도 추가했다. `GET /api/adaptive-rate-limit/status`를 치면 등록된 모든 CB의 현재 상태, 적용 중인 정책, 실패율, 버퍼 호출 수를 JSON으로 반환한다. 프론트엔드에서 3초 polling으로 보여준다.

이 패턴의 의미는 Circuit Breaker와 Rate Limiter를 독립적인 방어선으로 두는 게 아니라 연동하는 것이다. CB가 문제를 감지하면 Rate Limiter가 즉각 제동을 건다. CB가 회복하면 Rate Limiter도 조심스럽게 열린다. 두 레이어가 같은 신호를 보고 움직이는 게 훨씬 자연스럽다.

한 가지 아쉬운 점은 Rate Limit 정책이 전역적이라는 것이다. 같은 CB 이름이면 모든 클라이언트 IP에 동일한 정책이 적용된다. 사용자별로 다른 정책을 주고 싶다면 `resolveBucket(cbName, userKey)`에서 bucketKey를 조합해 개별 버킷을 만들면 된다. 그건 다음 단계다.
