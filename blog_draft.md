[Fullstack] 캐시 워밍 전략 - ApplicationReadyEvent + SSE로 콜드 스타트 DB 풀히트 없애기

---

2레벨 캐시(Caffeine + Redis)를 붙여두고 나서 한동안 뿌듯했다. L1 히트율이 높으면 Redis 왕복도 줄고, Redis 히트면 DB까지 안 간다. 이론적으로 완벽한 구조다.

그런데 배포 직후가 문제였다. 캐시가 완전히 비어있는 상태에서 트래픽이 들어오면, 처음 수백 건 요청이 전부 DB까지 직행한다. 운이 나쁘면 커넥션 풀이 순간적으로 고갈되고, 응답 시간이 튀는 게 보인다. 특히 이 프로젝트처럼 `resumeList`나 `trendStats`처럼 조회 빈도가 높은 데이터가 있으면 더 뼈아프다.

해결 방법은 명확하다. 앱이 완전히 뜬 직후, 실제 트래픽이 들어오기 전에 캐시를 미리 채워두면 된다. 이른바 캐시 워밍(Cache Warming)이다.

---

Spring에서 "앱이 완전히 준비됐을 때"를 감지하는 이벤트는 `ApplicationReadyEvent`다. `@PostConstruct`와 헷갈리기 쉬운데, `@PostConstruct`는 Bean 초기화 단계에서 실행되기 때문에 다른 Bean이 아직 완전히 준비되지 않은 시점일 수 있다. `ApplicationReadyEvent`는 모든 Bean 초기화와 Flyway 마이그레이션, 커넥션 풀 준비까지 다 끝난 뒤에 발행된다. 워밍처럼 DB 접근이 필요한 초기화 로직에는 반드시 이쪽을 써야 한다.

```kotlin
@EventListener(ApplicationReadyEvent::class)
fun warmOnStartup() {
    log.info("Cache warmup triggered by ApplicationReadyEvent")
    runWarmup(progressListener = null)
}
```

실제 워밍 로직은 두 단계다. `trendStats`는 최근 12개 레코드를 꺼내 각 id를 키로 캐시에 넣고, `resumeList`는 전체 카운트를 `"count"` 키로 넣는다. 카운트 하나만 넣는 이유는 이력서 전체 목록은 수천 건이 될 수 있어서 메모리 부담이 크기 때문이다. 실제 서비스라면 자주 쓰는 페이지(1페이지)만 워밍하는 식으로 확장하면 된다.

```kotlin
private fun warmTrendStats(listener: ((WarmupProgress) -> Unit)?): WarmupStepResult {
    val cacheName = "trendStats"
    return try {
        listener?.invoke(WarmupProgress(cacheName, "시작"))
        val stats = warmupTrendStore.findTop12()
        val cache = cacheManager.getCache(cacheName)
        stats.forEach { ts -> cache?.put(ts.id ?: return@forEach, ts) }
        listener?.invoke(WarmupProgress(cacheName, "완료: ${stats.size}건"))
        WarmupStepResult(cacheName, stats.size, null)
    } catch (e: Exception) {
        log.warn("[warmup] {} 실패: {}", cacheName, e.message)
        WarmupStepResult(cacheName, 0, e.message)
    }
}
```

한 가지 신경 쓴 부분은 `progressListener` 콜백이다. 워밍은 자동 실행이지만, 운영 중에 수동으로 다시 돌릴 필요가 생길 수도 있다. 그때 진행 상황을 실시간으로 보여주기 위해 SSE(Server-Sent Events)와 연결할 수 있도록 콜백을 열어뒀다.

```kotlin
@PostMapping("/trigger", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun trigger(): SseEmitter {
    val emitter = SseEmitter(60_000L)

    executor.submit {
        cacheWarmupService.runWarmup { progress ->
            emitter.send(
                SseEmitter.event()
                    .name("progress")
                    .data("${progress.cacheName}: ${progress.message}")
            )
        }
        emitter.send(SseEmitter.event().name("done").data("완료"))
        emitter.complete()
    }

    return emitter
}
```

SSE 엔드포인트를 별도 스레드(`VirtualThread`)에서 돌리는 건 중요하다. 워밍 작업이 메인 요청 스레드를 점유하면 타임아웃이 날 수 있다.

---

테스트 쪽은 포트 인터페이스 덕분에 깔끔하게 떨어졌다. 서비스가 `WarmupResumeStore`, `WarmupTrendStore` 인터페이스만 바라보니까, 테스트에선 Fake 구현 10줄로 끝난다. `SimpleCacheManager`에 `ConcurrentMapCache`를 꽂아서 실제 캐시 put/get 동작도 검증할 수 있었다.

```kotlin
class FakeWarmupTrendStore : WarmupTrendStore {
    var stats: List<TrendStats> = emptyList()
    var shouldThrow: Boolean = false

    override fun findTop12(): List<TrendStats> {
        if (shouldThrow) throw RuntimeException("DB connection failed")
        return stats
    }
}
```

예외 상황 테스트도 `shouldThrow = true` 한 줄로 커버된다. 실제로 워밍 실패가 앱 기동을 막아서는 안 되기 때문에, `try-catch`로 감싸서 스텝 단위로 오류를 기록하고 계속 진행하는 구조로 만들었다.

---

프론트엔드는 `/admin/cache-warmup` 페이지에 상태 카드, 스텝별 결과 테이블, 그리고 SSE 로그 뷰어를 붙였다. `EventSource`로 `/api/cache-warmup/trigger`를 구독하면 `progress` 이벤트가 날아오고, 완료되면 `done` 이벤트가 온다.

```typescript
const es = new EventSource('/api/cache-warmup/trigger');

es.addEventListener('progress', (e) => {
  setLogs((prev) => [...prev, `[진행] ${e.data}`]);
});

es.addEventListener('done', (e) => {
  setLogs((prev) => [...prev, `[완료] ${e.data}`]);
  es.close();
  setRunning(false);
  refetch();
});
```

---

구현하면서 느낀 건, 캐시 워밍 자체는 개념이 단순한데 "어떤 데이터를 얼마나 워밍할 것인가"가 진짜 고민이라는 점이다. 전체 이력서를 다 올리면 메모리 폭탄이고, 너무 적게 워밍하면 효과가 없다. 이 프로젝트에서는 집계성 데이터(trendStats 12개)와 카운트 정도만 올리는 걸로 타협했다. 실제 서비스라면 접근 패턴 분석 → 상위 N%를 워밍하는 식으로 발전시켜야 한다.

워밍 대상은 `TwoLevelCacheProperties.cacheNames`에 이미 정의된 이름들(`dashboard`, `jobApplications`, `resumeList`, `trendStats`)과 자연스럽게 맞물린다. 캐시 영역을 추가할 때 워밍 로직도 함께 고민하게 되는 구조가 만들어진 셈이다.
