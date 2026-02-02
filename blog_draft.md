
# [Fullstack] API Rate Limiting + Why Bucket4j?

오늘은 백엔드 API를 보호하기 위한 Rate Limiting(속도 제한) 기능을 구현했다.
서비스가 커지면 필연적으로 마주하게 되는 것이 트래픽 관리다. 악의적인 디도스(DDoS) 공격은 물론이고, 클라이언트 개발자의 실수로 인한 무한 루프 요청 등으로부터 서버 리소스를 보호해야 한다.

물론 앞단에 Nginx나 Spring Cloud Gateway 같은 게이트웨이가 있다면 거기서 처리하는 게 가장 효율적일 수 있다. 하지만 'Defense in Depth(심층 방어)' 원칙에 따라, 애플리케이션 레벨에서도 자체적인 방어막을 구축하는 것이 안전하다. 게이트웨이가 뚫리거나 우회되었을 때 최후의 보루가 되기 때문이다.

## 기술 선택: Bucket4j vs Redis/Lua

Rate Limiting을 구현하는 방법은 다양하다. Redis의 `INCR` 명령어와 만료 시간을 이용할 수도 있고, Lua 스크립트를 짤 수도 있다. 하지만 나는 **Bucket4j** 라이브러리를 선택했다.

이유는 단순하다. **알고리즘의 신뢰성**과 **구현의 편의성** 때문이다.
Bucket4j는 '토큰 버킷(Token Bucket)' 알고리즘을 아주 정교하게 구현해놨다. 단순히 카운트만 세는 게 아니라, 시간에 따라 토큰이 보충(Refill)되는 속도를 수학적으로 정확하게 제어한다. 또한, 트래픽이 적을 때는 로컬 캐시(Caffeine)만으로도 처리할 수 있어 Redis 네트워크 비용을 아낄 수 있다.

## 구현 내용

### 1. 의존성 추가
가볍게 `bucket4j-core`를 추가했다. Spring Boot Starter 버전도 있지만, 직접 제어하는 맛을 위해 코어 라이브러리를 사용했다. 또한 로컬 캐싱을 위해 `Caffeine`도 함께 사용했다.

```groovy
implementation 'com.github.ben-manes.caffeine:caffeine'
implementation 'com.bucket4j:bucket4j-core:8.10.1'
```

### 2. RateLimiterService
버킷을 관리하는 서비스다. IP 주소를 키(Key)로 사용하여 버킷을 생성하고 캐싱한다.
정책은 '1분에 20회 요청'으로 잡았다. 테스트용이라 좀 박하게 잡았지만, 실제 운영 환경에서는 API 중요도에 따라 다르게 설정하면 된다.

```kotlin
@Service
class RateLimiterService {
    private val cache: ConcurrentHashMap<String, Bucket> = ConcurrentHashMap()

    fun resolveBucket(key: String): Bucket {
        return cache.computeIfAbsent(key) { _ -> newBucket() }
    }

    private fun newBucket(): Bucket {
        // 1분에 20개 토큰 충전 (Greedy 방식)
        val limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)))
        return Bucket.builder().addLimit(limit).build()
    }
}
```

### 3. RateLimitFilter
`HandlerInterceptor` 대신 `Filter`를 선택했다. Spring Security보다 앞단에서 막기 위해서다. 인증되지 않은 사용자가 401 에러를 유발하는 요청을 무한정 보내면 DB나 인증 서버에 부하를 줄 수 있다. 그래서 제일 앞단인 Servlet Filter에서 IP 기반으로 쳐내는 것이 맞다고 판단했다.

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RateLimitFilter(
    private val rateLimiterService: RateLimiterService
) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        // ... (IP 추출 로직)
        val bucket = rateLimiterService.resolveBucket(ip)
        
        // 토큰 소비 시도 (남은 토큰 수 반환)
        val probe = bucket.tryConsumeAndReturnRemaining(1)

        if (probe.isConsumed) {
            httpResponse.addHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
            chain.doFilter(request, response)
        } else {
            httpResponse.status = 429
            httpResponse.addHeader("X-Rate-Limit-Retry-After-Seconds", probe.nanosToWaitForRefill.toString())
            httpResponse.writer.write("Too many requests.")
            return // 요청 차단
        }
    }
}
```

### 4. 프론트엔드 처리
서버가 429 상태 코드를 던지면, 클라이언트는 당황하지 않고 사용자에게 친절하게 알려줘야 한다. Axios Interceptor에 429 처리를 추가했다.

```typescript
if (error.response?.status === 429) {
    const retryAfter = error.response.headers['x-rate-limit-retry-after-seconds'];
    message = `요청이 너무 많습니다. ${retryAfter ? retryAfter + '초 뒤에 ' : ''}잠시 후 다시 시도해주세요.`;
}
```

## 마무리
테스트는 `MockMvc`와 `Mockito`를 이용해 단위 테스트로 검증했다. 통합 테스트를 하려면 Redis나 DB가 떠있어야 하는데, CI/CD 환경이나 로컬 환경의 제약을 받지 않도록 외부 의존성을 격리하는 것이 중요하다.

작은 기능이지만, 서비스의 안정성을 위해 꼭 필요한 안전벨트를 채운 기분이다. 이제 안심하고 더 복잡한 기능을 개발하러 갈 수 있겠다.
