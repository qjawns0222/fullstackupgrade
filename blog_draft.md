[Fullstack] 쿼리 계획 캐싱 & 힌트 자동 주입 - 반복 슬로우 쿼리에 Optimizer Hint를 동적으로 꽂다

---

슬로우 쿼리 감지 기능을 만들고 나서 한동안 뿌듯했다. `SlowQueryListener`가 300ms 넘는 쿼리를 잡아내고, EXPLAIN 결과를 Elasticsearch에 저장하고, 프론트엔드 대시보드에 경고를 띄우는 것까지 잘 돌아갔다. 그런데 어느 날 대시보드를 보다가 불편한 걸 발견했다. 같은 SQL 패턴이 계속 올라오고 있었다. "이미 알고 있어, 근데 아무것도 안 하잖아." 감지만 하고 대응이 없다. 진단 도구가 경고를 반복 출력하는 것뿐이라면 결국 알림 피로만 쌓인다.

그래서 이번엔 감지에서 멈추지 않고, 동일 패턴이 N회 이상 느려지면 Hibernate에 Optimizer Hint를 자동으로 주입하는 구조를 만들었다.

---

설계는 두 레이어로 나뉜다.

첫째는 `QueryHintRegistry`다. normalized SQL별로 슬로우 감지 횟수를 `ConcurrentHashMap<String, AtomicInteger>`로 추적하고, 횟수가 임계값(기본 3회)에 도달하는 순간 힌트 엔트리를 등록한다.

```kotlin
class QueryHintRegistry(private val hintThreshold: Int = 3) {

    private val slowCounts: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    private val hints: ConcurrentHashMap<String, QueryHintEntry> = ConcurrentHashMap()

    fun record(normalizedSql: String): Boolean {
        val count = slowCounts.getOrPut(normalizedSql) { AtomicInteger(0) }.incrementAndGet()
        if (count == hintThreshold && !hints.containsKey(normalizedSql)) {
            val hint = buildHint(normalizedSql)
            hints[normalizedSql] = QueryHintEntry(
                normalizedSql = normalizedSql,
                hint = hint,
                slowCount = count,
                registeredAt = LocalDateTime.now()
            )
            return true
        }
        return false
    }

    private fun buildHint(normalizedSql: String): String {
        val upper = normalizedSql.uppercase()
        return when {
            upper.contains("ORDER BY") -> "/*+ NO_FILESORT */"
            upper.contains("JOIN") -> "/*+ USE_INDEX_MERGE */"
            else -> "/*+ MAX_EXECUTION_TIME(5000) */"
        }
    }
}
```

힌트 선택 로직은 단순하게 갔다. ORDER BY가 있으면 파일 정렬 방지, JOIN이 있으면 인덱스 머지, 나머지는 실행 시간 제한. 정교한 쿼리 분석기를 만들 수도 있었지만, 지금 목적은 "반복 슬로우 쿼리를 자동으로 억제하는 것"이지 완벽한 옵티마이저가 아니다.

둘째는 `QueryHintInterceptor`다. Hibernate의 `StatementInspector` 인터페이스를 구현해서, 실제 SQL이 실행되기 직전에 레지스트리를 조회하고 힌트를 앞에 붙인다.

```kotlin
class QueryHintInterceptor(private val registry: QueryHintRegistry) : StatementInspector {

    override fun inspect(sql: String): String {
        val normalized = QueryExecutionContext.normalize(sql)
        val hint = registry.getHint(normalized) ?: return sql
        return "$hint $sql"
    }
}
```

`QueryExecutionContext.normalize()`는 이미 N+1 감지용으로 만들어둔 함수라서 그대로 재사용했다. 숫자 리터럴과 문자열 리터럴을 `?`로 치환해서 `WHERE id = 1`과 `WHERE id = 42`가 같은 패턴으로 인식되게 한다.

---

Hibernate에 StatementInspector를 주입하는 방법이 처음엔 막막했다. 공식 문서를 찾아보니 `HibernatePropertiesCustomizer` 빈을 등록해서 `hibernate.session_factory.statement_inspector` 프로퍼티에 인스턴스를 넣으면 된다.

```kotlin
@Configuration
class HibernateStatementInspectorConfig {

    @Bean
    fun queryHintHibernateCustomizer(registry: QueryHintRegistry): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { props ->
            props[AvailableSettings.STATEMENT_INSPECTOR] = QueryHintInterceptor(registry)
        }
}
```

`AvailableSettings.STATEMENT_INSPECTOR`가 문자열 상수 오타를 방지해준다. 이런 건 직접 문자열로 쓰지 않는 게 맞다.

---

`SlowQueryListener`에는 `hintRegistry?.record(normalized)` 한 줄만 추가했다. null 안전 호출로 처리해서 `DataSourceProxyConfig`에서 registry가 없는 환경(테스트 등)에서도 안전하게 동작한다.

`QueryMonitorProperties`에 `hintThreshold` 설정을 추가해서 `application.yml`에서 조정 가능하게 했다.

```yaml
query:
  monitor:
    hint-threshold: 3
```

---

구현하면서 예상치 못한 부분이 있었다. `StatementInspector`는 Hibernate가 SQL을 생성한 직후, JDBC로 넘기기 직전에 호출된다. 즉 datasource-proxy의 `afterQuery`와는 실행 시점이 다르다. registry에 힌트가 등록되는 건 `afterQuery`(실행 후)고, 힌트가 주입되는 건 `inspect`(실행 전)다. 따라서 힌트 주입은 정확히 threshold+1번째 실행부터 적용된다. 처음 3번은 느리게 실행되고, 4번째부터 힌트가 붙는다. 이게 의도한 동작이기도 하고, 어차피 이미 느린 쿼리에 대한 사후 조치이므로 문제없다.

---

REST API는 간단하게 뽑았다.

```
GET  /api/query-hints       → 등록된 힌트 전체 목록
DELETE /api/query-hints?sql=... → 특정 패턴 힌트 제거
DELETE /api/query-hints/all     → 전체 초기화
```

프론트엔드 `/admin/query-hint` 페이지는 기존 DLQ 대시보드 스타일을 따라서 5초 폴링으로 현황을 보여주고, 개별 힌트 제거와 전체 초기화 버튼을 달았다.

테스트는 `QueryHintRegistry`와 `QueryHintInterceptor`를 순수 단위 테스트로 작성했다. Spring Context 없이 인스턴스를 직접 생성하는 방식이라 빠르고 격리가 잘 된다. "threshold 정확히 도달 시 true 반환", "ORDER BY면 NO_FILESORT 힌트", "remove 후 카운트 초기화" 같은 케이스들을 명시적으로 검증했다.

이제 슬로우 쿼리 대시보드가 단순한 경보 장치가 아니라, 반복 문제에 자동으로 대응하는 구조가 됐다.
