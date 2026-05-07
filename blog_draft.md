[Fullstack] A/B 테스트 프레임워크 - Unleash Variant API로 메서드 단위 코호트 실험을 꽂다

---

Unleash Feature Flag를 쓰면서 항상 아쉬웠던 게 있었다. 켜짐/꺼짐은 잘 됐는데, "어떤 사용자에게 어떤 UI를 보여줬고 그 결과가 어땠는지"를 체계적으로 추적하는 구조가 없었다. isEnabled()로 분기는 가능하지만, 실험 결과를 DB에 남기고 통계를 뽑으려면 그걸 호출하는 쪽에서 직접 저장 로직을 넣어야 했다. 기능마다 반복되는 보일러플레이트였고, 나중에 퍼널 분석과 연결하려면 더 복잡해질 게 뻔했다.

그래서 이번엔 `@ABTest` 어노테이션 하나로 메서드에 실험을 붙이고, AOP가 variant 배정과 기록을 전부 처리하는 구조를 만들었다.

---

설계의 핵심은 두 가지다.

첫째, Unleash의 `getVariant(toggleName, context)` API를 활용한다. `isEnabled()`는 켜짐/꺼짐만 알려주지만, `getVariant()`는 `Variant` 객체를 반환하는데 여기에 `name` (A/B/control 같은 variant 이름), `payload` (JSON이나 문자열 페이로드), `enabled` (토글이 켜져 있는지)가 담겨 있다. 토글이 꺼져 있으면 `Variant.DISABLED_VARIANT`가 반환되고 name은 "disabled"다. 이걸 그대로 저장하면 실험 미노출도 추적 가능하다.

둘째, `AbTestVariantHolder`라는 ThreadLocal 홀더다. AOP가 variant를 결정한 뒤 ThreadLocal에 넣어두면, 메서드 내부에서 `AbTestVariantHolder.get()`으로 현재 variant를 꺼낼 수 있다. finally 블록에서 반드시 clear()한다.

```kotlin
@Aspect
@Component
class AbTestAspect(
    private val unleash: Unleash,
    private val service: AbTestService
) {
    @Around("@annotation(abTest)")
    fun applyVariant(joinPoint: ProceedingJoinPoint, abTest: ABTest): Any? {
        val context = UnleashContext.builder().build()
        val variant = unleash.getVariant(abTest.toggleName, context)

        AbTestVariantHolder.set(variant.name)
        return try {
            val result = joinPoint.proceed()
            if (abTest.trackEvent) {
                val payload = variant.payload.map { it.value }.orElse(null)
                service.recordVariant(
                    toggleName = abTest.toggleName,
                    variantName = variant.name,
                    userId = null,
                    sessionId = null,
                    payload = payload
                )
            }
            result
        } finally {
            AbTestVariantHolder.clear()
        }
    }
}
```

어노테이션 사용은 이렇게 된다.

```kotlin
@ABTest(toggleName = "checkout-flow", trackEvent = true)
fun renderCheckout(userId: String): CheckoutView {
    return when (AbTestVariantHolder.get()) {
        "B" -> newCheckoutView(userId)
        else -> legacyCheckoutView(userId)
    }
}
```

메서드 안에서 variant 이름으로 분기하는 게 전부다. AOP가 Unleash 호출과 DB 저장을 처리하기 때문에 비즈니스 로직에 실험 코드가 섞이지 않는다.

---

데이터 레이어는 포트 인터페이스 패턴으로 분리했다. `AbTestStore`가 서비스가 의존하는 인터페이스고, `JpaAbTestStore`가 실제 MariaDB 구현이다. 테스트에서는 `FakeAbTestStore`를 10줄로 만들어서 쓴다.

```kotlin
interface AbTestStore {
    fun save(result: AbTestResult): AbTestResult
    fun countByToggleAndVariantSince(toggleName: String, since: LocalDateTime): Map<String, Long>
    fun findRecentByToggle(toggleName: String, limit: Int): List<AbTestResult>
}
```

통계 쿼리는 JPQL GROUP BY로 처리했다.

```kotlin
@Query("""
    SELECT a.variantName, COUNT(a) FROM AbTestResult a
    WHERE a.toggleName = :toggleName AND a.recordedAt >= :since
    GROUP BY a.variantName
""")
fun countByVariantSince(toggleName: String, since: LocalDateTime): List<Array<Any>>
```

---

구현 중에 실수가 하나 있었다. Flyway 마이그레이션을 V10으로 만들었는데, `V10__add_user_events_table.sql`이 이미 존재했다. 팀에서 쓰는 Flyway는 같은 버전 번호를 감지하면 바로 FlywayException을 던지고 컨텍스트 로딩이 실패한다. 당연히 SchemaMigrationTest가 터졌고, 그 컨텍스트 오염으로 JwtAuthenticationFilterTest 등 전혀 관련 없는 테스트들도 도미노처럼 실패했다. V14로 변경해서 해결했다.

또 H2 인라인 INDEX 문법 문제도 있었다. MariaDB에서는 CREATE TABLE 안에 `INDEX idx_name (col)` 형태가 가능하지만, H2는 이걸 모른다. `Unknown data type: "IDX_ABT_TOGGLE_RECORDED"` 에러가 떴다. CREATE INDEX를 별도 문장으로 분리하는 것으로 해결했다.

```sql
CREATE TABLE IF NOT EXISTS ab_test_results (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    toggle_name  VARCHAR(100) NOT NULL,
    variant_name VARCHAR(100) NOT NULL,
    user_id      VARCHAR(100),
    session_id   VARCHAR(100),
    payload      TEXT,
    recorded_at  DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_abt_toggle_recorded ON ab_test_results (toggle_name, recorded_at);
```

H2 호환을 체크하는 SchemaMigrationTest가 이런 이슈를 잡아주는 게 역시 중요하다.

---

테스트는 FakeUnleash를 적극 활용했다. Unleash 공식 JAR에 `FakeUnleash`가 내장돼 있고, `setVariant(toggleName, Variant)` 메서드로 원하는 variant를 주입할 수 있다.

```kotlin
@Test
fun `getVariant records result in store`() {
    fakeUnleash.enable("my-toggle")
    fakeUnleash.setVariant("my-toggle", Variant("B", null as String?, true))

    service.getVariant("my-toggle", userId = "user1", sessionId = "sess-1")

    assertEquals(1, fakeStore.saved.size)
    assertEquals("B", fakeStore.saved.first().variantName)
}
```

Kotlin에서 `Variant("B", null as String?, true)` — null에 타입 캐스팅이 필요한 게 약간 어색하지만, 생성자 오버로딩 매칭 때문에 어쩔 수 없었다.

---

프론트엔드는 `/admin/ab-test` 페이지로 만들었다. variant 배정 테스트 폼, 토글별 분포 바 차트, 최근 배정 이력 테이블을 5초 polling으로 보여준다. Unleash 서버가 없는 환경에서는 "disabled" variant가 100% 나오지만 그것도 데이터로 기록된다.

지금 구조는 AOP에서 사용자 ID를 null로 처리하고 있는데, 실제로는 Spring Security의 SecurityContextHolder에서 현재 로그인 사용자를 꺼내 넣어주는 게 맞다. 다음 단계로 퍼널 이벤트(`UserEvent`)와 연결할 때 같이 처리할 것 같다.
