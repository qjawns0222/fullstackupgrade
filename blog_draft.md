[Fullstack] 메서드 레벨 타이밍 추적 - @WithSpan AOP와 병목 감지 대시보드

---

Micrometer로 엔드포인트 응답 시간은 잡히는데, 그 안에서 어느 메서드가 병목인지는 전혀 보이지 않았다. `/api/resumes`가 800ms인데 OCR인지, DB인지, 캐시 미스인지 알 수 없었다. Zipkin 같은 분산 트레이싱 도구를 붙이면 좋겠지만, 지금 프로젝트에서 그 정도 인프라를 셋업하기엔 오버스펙이다.

그래서 선택한 방법은 간단하다. Spring AOP로 어노테이션 기반 메서드 타이밍 측정기를 직접 만들었다.

---

핵심 아이디어는 `@WithSpan` 어노테이션 하나다.

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithSpan(
    val name: String = "",
    val slowThresholdMs: Long = 500L
)
```

이걸 원하는 메서드에 붙이면 AOP가 실행 시간을 측정해서 DB에 저장한다.

```kotlin
@Aspect
@Component
class WithSpanAspect(private val spanStore: SpanStore) {

    @Around("@annotation(withSpan)")
    fun trace(joinPoint: ProceedingJoinPoint, withSpan: WithSpan): Any? {
        val signature = joinPoint.signature as MethodSignature
        val className = signature.declaringTypeName.substringAfterLast('.')
        val methodName = signature.name
        val spanName = withSpan.name.ifBlank { "$className.$methodName" }

        val startMs = System.currentTimeMillis()
        var status = "SUCCESS"
        var errorMessage: String? = null

        return try {
            val result = joinPoint.proceed()
            val durationMs = System.currentTimeMillis() - startMs
            if (durationMs >= withSpan.slowThresholdMs) {
                status = "SLOW"
                logger.warn("[WithSpan] SLOW: {} took {}ms", spanName, durationMs)
            }
            result
        } catch (ex: Throwable) {
            status = "ERROR"
            errorMessage = ex.message?.take(500)
            throw ex
        } finally {
            val durationMs = System.currentTimeMillis() - startMs
            spanStore.save(SpanRecord(
                spanName = spanName,
                className = className,
                methodName = methodName,
                durationMs = durationMs,
                status = status,
                errorMessage = errorMessage
            ))
        }
    }
}
```

status가 SUCCESS/SLOW/ERROR 세 가지인 게 포인트다. 에러가 없어도 임계값(기본 500ms)을 넘으면 SLOW로 분류해서 따로 조회할 수 있다.

---

데이터 레이어는 포트 인터페이스 패턴으로 분리했다.

```kotlin
interface SpanStore {
    fun save(record: SpanRecord): SpanRecord
    fun findRecent(limit: Int): List<SpanRecord>
    fun findSlowSpans(thresholdMs: Long, limit: Int): List<SpanRecord>
    fun stats(): SpanStats
}
```

`JpaSpanStore`가 실제 구현이고, 테스트에서는 `FakeSpanStore`로 10줄짜리 인메모리 구현을 쓴다. 덕분에 `WithSpanAspect` 단위 테스트가 Spring Context 없이 깔끔하게 돌아간다.

---

구현 중에 재미있는 문제가 하나 있었다. 테스트에서 `mock(WithSpan::class.java)`로 어노테이션을 목킹했는데, `signature.method.name`이 항상 "toString"을 반환했다. Mockito가 `sig.method`에 `String::class.java.getMethod("toString")`을 반환하도록 stubbing했기 때문이다.

해결은 간단했다. `signature.method.name` 대신 `signature.name`을 쓰면 된다. `MethodSignature.name`은 AspectJ가 실제 인터셉트된 메서드 이름을 직접 들고 있어서 mock에서도 제대로 동작한다.

```kotlin
// 이전 (mock에서 "toString" 반환)
val methodName = signature.method.name

// 수정 후
val methodName = signature.name
```

---

REST API는 세 개다.

```
GET /api/tracing/stats           → 전체/SLOW/ERROR 카운트 + 평균 응답시간
GET /api/tracing/recent?limit=50 → 최근 Span 목록
GET /api/tracing/slow?thresholdMs=500&limit=50 → 임계값 초과 Span 목록
```

프론트엔드는 `/admin/tracing`에 TanStack Query 5초 폴링으로 실시간 대시보드를 붙였다. 상단에 통계 카드 4개(전체, SLOW, ERROR, 평균 응답), 하단에 최근 Span/SLOW Span 탭 전환 테이블이다. 응답시간 컬럼에서 임계값 초과 항목은 노란색으로 강조된다.

---

실제로 써보면 꽤 유용하다. OCR 서비스에 `@WithSpan(name = "ocr.process", slowThresholdMs = 3000L)` 붙이고, 캐시 조회에 `@WithSpan(slowThresholdMs = 100L)` 붙이면 대시보드에서 즉시 보인다. Zipkin 없이도 "어느 메서드가 얼마나 걸리는지" 한눈에 파악할 수 있다.

Flyway V8 마이그레이션으로 `span_records` 테이블을 추가했고, `recorded_at DESC`, `status`, `duration_ms DESC` 세 인덱스를 걸었다. 장기적으로는 오래된 레코드 자동 정리 스케줄러가 필요하겠지만, 일단은 조회 성능만 챙겼다.
