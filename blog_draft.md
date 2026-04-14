[Fullstack] 사용자 행동 퍼널 분석 - 이력서 조회→저장→다운로드 전환율 추적

---

감사 로그는 이미 있었다. 누가 언제 어떤 API를 호출했는지 Elasticsearch에 다 쌓이고 있었다. 그런데 "이력서를 조회한 사람 중 몇 퍼센트가 저장하고, 저장한 사람 중 몇 퍼센트가 다운로드하는가"를 물어보면 아무것도 없었다. 로그는 있는데 퍼널은 없는 상태. 이게 생각보다 쓸모없다. 전환율 없이는 어느 단계에서 사용자가 이탈하는지 알 수 없다.

Amplitude 같은 외부 SaaS를 붙이는 방법도 있다. `features-todo.md`에 `com.amplitude:java-sdk:1.10.2`를 쓰는 방안이 적혀 있었다. 그런데 외부 SDK를 도입하면 이벤트 스키마가 외부 시스템에 종속된다. 직접 MariaDB에 이벤트를 쌓고 집계 쿼리로 퍼널을 계산하는 게 더 직관적이고, 이 프로젝트에서 이미 쓰는 스택에서 벗어나지 않는다. 외부 의존성 없이 같은 기능을 만들 수 있다면 굳이 SDK를 추가할 필요가 없다.

---

설계는 단순하게 잡았다. 이벤트 하나를 DB에 저장하고, 저장된 이벤트를 집계해서 퍼널 통계를 내는 것. 퍼널 단계는 세 개다: `RESUME_VIEW`, `RESUME_SAVE`, `RESUME_DOWNLOAD`.

테이블부터 만들었다.

```sql
CREATE TABLE user_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(100) NOT NULL,
    user_id     VARCHAR(100),
    event_type  VARCHAR(50)  NOT NULL,
    resource_id VARCHAR(200),
    metadata    TEXT,
    occurred_at DATETIME(6)  NOT NULL
);

CREATE INDEX idx_user_events_session_id  ON user_events (session_id);
CREATE INDEX idx_user_events_event_type  ON user_events (event_type);
CREATE INDEX idx_user_events_occurred_at ON user_events (occurred_at DESC);
CREATE INDEX idx_user_events_user_id     ON user_events (user_id);
```

`session_id`가 핵심이다. 퍼널 분석에서 "한 사람이 조회 후 저장했다"를 추적하려면 같은 세션에서 발생한 이벤트를 묶어야 한다. `user_id`는 nullable로 뒀다. 로그인하지 않은 상태에서도 세션 기반으로 추적하고 싶었기 때문이다.

---

데이터 레이어는 이 프로젝트의 표준 패턴을 그대로 따랐다. 서비스가 JpaRepository를 직접 보지 않고, 포트 인터페이스를 통한다.

```kotlin
interface UserEventStore {
    fun save(event: UserEvent): UserEvent
    fun countSessionsByEventTypeSince(since: LocalDateTime): Map<String, Long>
    fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime): Long
}
```

JPA 어댑터는 JPQL 집계 쿼리를 쓴다.

```kotlin
@Query("""
    SELECT u.eventType, COUNT(DISTINCT u.sessionId)
    FROM UserEvent u
    WHERE u.occurredAt >= :since
    GROUP BY u.eventType
""")
fun countSessionsByEventTypeSince(since: LocalDateTime): List<Array<Any>>
```

`COUNT(DISTINCT u.sessionId)`로 유니크 세션을 센다. 한 세션에서 같은 이력서를 두 번 조회해도 1로 카운트된다. 이게 퍼널 분석에서 맞는 방식이다. 단순 이벤트 수가 아니라 "몇 명이 이 단계를 거쳤나"를 측정해야 하니까.

---

집계 로직은 서비스에서 처리한다.

```kotlin
@Service
class FunnelAnalysisService(private val store: UserEventStore) {

    companion object {
        val FUNNEL_STEPS = listOf(
            "RESUME_VIEW",
            "RESUME_SAVE",
            "RESUME_DOWNLOAD"
        )
    }

    fun getFunnelStats(periodHours: Int = 24): FunnelStats {
        val since = LocalDateTime.now().minusHours(periodHours.toLong())
        val countsByType = store.countSessionsByEventTypeSince(since)

        val topCount = FUNNEL_STEPS.firstOrNull()
            ?.let { countsByType[it] ?: 0L }
            ?: 0L

        val steps = FUNNEL_STEPS.map { eventType ->
            val count = countsByType[eventType] ?: 0L
            FunnelStep(
                eventType = eventType,
                sessionCount = count,
                conversionRate = if (topCount == 0L) 0.0
                    else Math.round(count.toDouble() / topCount * 1000) / 10.0
            )
        }

        return FunnelStats(steps = steps, totalSessions = topCount, periodHours = periodHours)
    }
}
```

전환율 계산에서 소수점 처리를 `Math.round(x * 1000) / 10.0`으로 했다. 부동소수점 오차로 50.00000000001 같은 값이 나오지 않도록. 별거 아닌 것 같아도 UI에 표시될 때 이런 숫자가 나오면 지저분하다.

---

테스트는 `FakeUserEventStore`로 작성했다. Spring Context가 필요 없어서 빠르게 돈다.

```kotlin
class FakeUserEventStore : UserEventStore {
    val saved = mutableListOf<UserEvent>()
    val sessionCounts = mutableMapOf<String, Long>()
    private var idSeq = 1L

    override fun save(event: UserEvent) = event.copy(id = idSeq++).also { saved.add(it) }
    override fun countSessionsByEventTypeSince(since: LocalDateTime) = sessionCounts.toMap()
    override fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime) =
        sessionCounts[eventType] ?: 0L
}
```

전환율 계산이 맞는지 확인하는 테스트가 핵심이다.

```kotlin
@Test
fun `getFunnelStats returns steps with correct conversion rates`() {
    store.sessionCounts["RESUME_VIEW"] = 10L
    store.sessionCounts["RESUME_SAVE"] = 5L
    store.sessionCounts["RESUME_DOWNLOAD"] = 2L

    val stats = service.getFunnelStats(24)

    assertEquals(100.0, stats.steps[0].conversionRate)  // 기준
    assertEquals(50.0, stats.steps[1].conversionRate)   // 5/10
    assertEquals(20.0, stats.steps[2].conversionRate)   // 2/10
}
```

---

API는 두 개다.

```
POST /api/funnel/events   → 이벤트 기록
GET  /api/funnel/stats    → 퍼널 통계 (periodHours 파라미터)
```

`periodHours`는 기본값 24. 6시간, 24시간, 48시간, 7일 단위로 필터링할 수 있다.

프론트엔드는 `/admin/funnel`에 붙였다. 퍼널 단계별 바 차트를 그리는데, 막대 너비는 최대값 기준 상대값으로 계산한다. 절대 수치를 그대로 픽셀로 쓰면 데이터가 적을 때 막대가 거의 안 보인다.

```tsx
const widthPct = maxCount === 0 ? 0 : (step.sessionCount / maxCount) * 100;
```

단계 사이에 이탈 수도 표시한다. "조회 10명 중 5명 저장 → 5명 이탈"처럼. 이게 있어야 어느 단계에서 막히는지 바로 보인다.

---

구현하면서 확인한 것 하나. JPQL에서 `COUNT(DISTINCT)`와 `GROUP BY`를 함께 쓸 때 반환 타입이 `List<Array<Any>>`다. `List<Pair<String, Long>>` 같은 타입으로 매핑이 안 된다. 인터페이스 선언을 `List<Array<Any>>`로 박아두고 어댑터에서 변환하는 게 맞다.

```kotlin
override fun countSessionsByEventTypeSince(since: LocalDateTime): Map<String, Long> =
    repo.countSessionsByEventTypeSince(since)
        .associate { row -> row[0] as String to row[1] as Long }
```

`row[0]`이 String, `row[1]`이 Long인 걸 런타임에 캐스팅한다. 좀 불안하지만 JPQL GROUP BY 결과에서 타입 순서는 쿼리 작성 순서와 일치하니까 실용적으로는 문제없다.
