[Fullstack] 분산 환경을 위한 Bucket4j와 Redis 기반 Rate Limiting 구축 경험기

서비스 트래픽이 꾸준히 늘어남에 따라 백엔드 애플리케이션의 스케일 아웃(Scale-out)을 고민하게 마련이다. 하지만 기존 코드 베이스를 점검하던 중 아주 치명적인 설계 결함을 발견했다. 바로 API 요청 횟수를 제한하는 Rate Limiter가 애플리케이션 메모리 내부(`ConcurrentHashMap`)에서 동작하고 있다는 점이었다.

서버가 1대일 때는 전혀 문제가 되지 않지만, 만약 서버가 3대로 늘어난다면 각 서버가 독립적으로 한도를 계산하게 되어 실질적으로 사용자가 제한보다 3배 더 많은 요청을 보낼 수 있게 되는 데이터 정합성 깨짐 문제가 발생한다. 이런 인프라적 한계를 극복하기 위해 기존 인메모리 캐시를 버리고, 모든 서버가 상태를 공유하는 **Redis 기반 분산 Rate Limiting** 시스템으로 마이그레이션(Migration)을 진행했다.

### 왜 Bucket4j + Redis 인가?
단일 노드에서는 Caffeine 등 단순 캐시가 성능에 유리하지만, 클러스터링 기반에서는 이를 중앙화해야 한다. 기존 프로젝트에서 이미 Spring Data Redis(+ Lettuce) 환경을 갖추고 있었으므로 부하 분산과 동시성 문제 처리가 검증된 `bucket4j-redis` 라이브러리를 채택했다.

### 구현 상세 (핵심 코드 스니펫)

기존에는 객체를 맵에 담기만 하면 끝이었지만, 이번에는 Lettuce 기반의 `ProxyManager`를 Bean으로 등록해 분산 저장소가 버킷 트랜잭션을 처리하도록 만들었다.

#### 1. RedisBucketConfig 설정
Spring Boot가 제공하는 `LettuceConnectionFactory`의 네이티브 클라이언트를 어댑터로 감싸 `LettuceBasedProxyManager`를 Bean으로 정의한다. 이제 이 ProxyManager만이 글로벌 Rate Limit을 주관하게 된다.

```kotlin
package com.example.demo.config

import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

@Configuration
class RedisBucketConfig {
    @Bean
    fun proxyManager(lettuceConnectionFactory: LettuceConnectionFactory): ProxyManager<ByteArray> {
        val client = lettuceConnectionFactory.nativeClient as RedisClient
        return LettuceBasedProxyManager.builderFor(client).build()
    }
}
```

#### 2. RateLimiterService 리팩토링
가장 큰 변화는 `ConcurrentHashMap`이 사라지고 `ProxyManager`가 주입되어 동작한다는 것이다. 이제 클라이언트 IP 기반 Bucket은 Redis에서 조회(또는 생성)되며, 동시에 발생하는 수천 건의 요청도 원자적(Atomic)으로 검사된다. 게다가 deprecate된 기존 API들을 현대적인 `Bandwidth.builder()` 방식으로 교체해 경고 메시지도 모두 정리했다.

```kotlin
package com.example.demo.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.distributed.proxy.ProxyManager
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.springframework.stereotype.Service

@Service
class RateLimiterService(private val proxyManager: ProxyManager<ByteArray>) {

    fun resolveBucket(key: String): Bucket {
        val bytes = key.toByteArray(StandardCharsets.UTF_8)
        return proxyManager.builder().build(bytes) {
            // 1분당 최대 20개의 요청만 허용 (글로벌 제한 적용)
            val limit = Bandwidth.builder()
                .capacity(20)
                .refillGreedy(20, Duration.ofMinutes(1))
                .build()
            io.github.bucket4j.BucketConfiguration.builder().addLimit(limit).build()
        }
    }
}
```

### 마치며
애플리케이션 코드는 종종 "돌아가기만 하면 된다"는 착각에 빠지게 만든다. 단일 서버에서 테스트하던 코드가 분산 아키텍처 위로 올라갔을 때 마주할 수 있는 전형적인 트래픽 한계(bottleneck)와 정합성 모순을 해결한 가치 있는 경험이었다. Mock 객체를 통해 JUnit 5 테스트도 완벽히 통과하도록 수정했으며, 이제 마음 놓고 서버를 증설(Scale-out)할 수 있게 되었다.
