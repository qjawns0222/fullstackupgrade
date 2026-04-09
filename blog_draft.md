[Fullstack] Testcontainers MariaDB 마이그레이션 자동 검증 - Flyway 스키마 오류를 배포 전에 잡는 법

---

Flyway를 쓰면서 한동안은 괜찮았다. 마이그레이션 파일을 작성하고, H2 기반 테스트가 초록불이 뜨면 배포했다. 그러다 어느 날 운영 배포 후 `Table 'study_db.key_rotation_history' doesn't exist`라는 에러가 Sentry에 찍혔다. 원인을 파보니 H2에서는 잘 돌던 마이그레이션 SQL이 MariaDB에서는 실패하고 있었다. `INDEX idx_name (col)` 인라인 문법이 H2에서는 통과되지만 MariaDB와의 동작 차이로 인해 Flyway가 실제 DB에서 중단된 것이다.

H2 호환 모드(`MODE=MySQL`)를 쓰면 어느 정도 커버되지만, 완전히 동일하지는 않다. 테스트는 초록불인데 운영은 빨간불인 상황이 생긴다. 이걸 근본적으로 해결하려면 테스트에서도 실제 MariaDB를 써야 한다.

Testcontainers가 이 문제를 깔끔하게 풀어준다. Docker로 MariaDB 컨테이너를 테스트 시간에 올리고, 실제 Flyway 마이그레이션을 돌린 뒤, 테이블 구조와 외래키까지 검증한다.

---

핵심 코드부터 보자.

```kotlin
@Testcontainers(disabledWithoutDocker = true)
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MariaDbMigrationTest {

    companion object {
        @Container
        @JvmStatic
        val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:10.11")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mariadb.jdbcUrl }
            registry.add("spring.datasource.username") { mariadb.username }
            registry.add("spring.datasource.password") { mariadb.password }
            registry.add("spring.datasource.driver-class-name") { "org.mariadb.jdbc.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.baseline-on-migrate") { "true" }
        }
    }
```

`disabledWithoutDocker = true` 옵션이 중요하다. Docker가 없는 환경(GitHub Actions 일부 러너, 로컬 개발 머신)에서는 테스트를 자동으로 skip한다. Docker가 있는 CI 환경에서만 실제로 돌아간다.

`@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`은 Spring이 H2로 DataSource를 자동 교체하는 걸 막는다. 컨테이너의 MariaDB를 그대로 쓰겠다는 선언이다.

---

테스트 내용은 단순하다. 마이그레이션이 성공적으로 실행됐는지, 테이블이 존재하는지, 컬럼이 올바른지, 외래키가 걸려있는지, 실제 INSERT/SELECT가 되는지까지 검증한다.

```kotlin
@Test
fun `V1 users 테이블이 올바른 컬럼과 함께 생성된다`() {
    val columns = getColumnNames("users")
    assertThat(columns).containsExactlyInAnyOrder("id", "username", "password", "role", "email")
}

@Test
fun `외래키 제약조건 - resumes는 users를 참조한다`() {
    val fkCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'resumes'
          AND REFERENCED_TABLE_NAME = 'users'
        """.trimIndent(),
        Long::class.java
    ) ?: 0L
    assertThat(fkCount).isGreaterThanOrEqualTo(1L)
}

@Test
fun `ORM 매핑 검증 - users 테이블에 데이터를 삽입하고 조회할 수 있다`() {
    jdbcTemplate.execute(
        "INSERT INTO users (username, password, role, email) VALUES ('migration_test_user', 'pw', 'USER', 'test@test.com')"
    )
    val count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE username = 'migration_test_user'",
        Long::class.java
    ) ?: 0L
    assertThat(count).isEqualTo(1L)
}
```

컬럼명 검증은 `information_schema.COLUMNS`를 직접 쿼리한다. 이렇게 하면 엔티티 클래스가 아닌 실제 DB 스키마를 기준으로 검증하기 때문에 JPA 매핑 오류를 배포 전에 잡을 수 있다.

---

구현하면서 예상치 못한 문제를 두 개 만났다.

첫 번째는 V5, V6 마이그레이션의 인라인 INDEX 문법이었다.

```sql
-- 기존 (H2에서만 동작)
CREATE TABLE dlq_messages (
    ...
    INDEX idx_dlq_status (dlq_status),
    INDEX idx_failed_at (failed_at)
);
```

이 구문이 MariaDB에서는 파싱 오류를 냈다. `CREATE TABLE` 안에 `INDEX` 절을 쓰는 방식이 H2와 MariaDB 사이에 미묘하게 달랐다. 해결은 간단했다. 테이블 생성 후 별도 `CREATE INDEX`로 분리했다.

```sql
-- 수정 후 (H2 + MariaDB 모두 동작)
CREATE TABLE dlq_messages (
    ...
    last_error TEXT
);

CREATE INDEX idx_dlq_status ON dlq_messages (dlq_status);
CREATE INDEX idx_failed_at ON dlq_messages (failed_at);
```

이 문제가 기존 `SchemaMigrationTest`(H2 기반)에서는 전혀 감지되지 않았다는 게 포인트다. H2가 관대하게 처리해준 덕분에 그냥 넘어갔던 것이다.

두 번째는 Spring 컨텍스트 오염 문제였다. `JpaApiSnapshotStore`라는 클래스가 `ApiSnapshotStore`와 `ApiBreakingChangeStore` 두 인터페이스를 동시에 구현하고 있었는데, 두 인터페이스 모두 `findAllDesc()`를 선언하고 있었다. 반환 타입이 달라서 Kotlin 컴파일러가 충돌로 판단했다.

```
Conflicting overloads: public open fun findAllDesc(): List<ApiSnapshot>
defined in JpaApiSnapshotStore, public open fun findAllDesc(): List<ApiBreakingChange>
defined in JpaApiSnapshotStore
```

기존 코드가 컴파일 자체가 안 되는 상태였다. 두 인터페이스를 각각 별도 `@Component`로 구현해서 분리했다.

```kotlin
@Component
class JpaApiSnapshotStore(
    private val snapshotRepo: ApiSnapshotRepository
) : ApiSnapshotStore {
    override fun findAllDesc() = snapshotRepo.findAllOrderByCreatedAtDesc()
    // ...
}

@Component
class JpaApiBreakingChangeStore(
    private val breakingRepo: ApiBreakingChangeRepository
) : ApiBreakingChangeStore {
    override fun findAllDesc() = breakingRepo.findAllByOrderByDetectedAtDesc()
    // ...
}
```

---

프론트엔드에는 `/admin/migration` 대시보드를 추가했다. 5초마다 `/api/migration/status`를 폴링해서 현재 Flyway 버전, 전체/적용/실패/대기 건수를 카드로 보여준다. 실패한 마이그레이션이 있으면 빨간 경고 배너도 뜬다.

```kotlin
@RestController
@RequestMapping("/api/migration")
class MigrationStatusController(private val flyway: Flyway) {

    @GetMapping("/status")
    fun getStatus(): MigrationStatusResponse {
        val info = flyway.info()
        val all = info.all()
        val applied = all.filter { it.state == MigrationState.SUCCESS }
        val failed = all.filter { it.state == MigrationState.FAILED }
        val pending = all.filter { it.state == MigrationState.PENDING }

        return MigrationStatusResponse(
            total = all.size,
            applied = applied.size,
            failed = failed.size,
            pending = pending.size,
            currentVersion = info.current()?.version?.version ?: "none",
            migrations = all.map { m ->
                MigrationInfo(
                    version = m.version?.version ?: "repeatable",
                    description = m.description ?: "",
                    type = m.type?.toString() ?: "UNKNOWN",
                    state = m.state?.name ?: "UNKNOWN",
                    installedOn = m.installedOn?.let { formatter.format(it.toInstant()) },
                    executionTime = m.executionTime
                )
            }
        )
    }
}
```

---

Testcontainers 접근법의 단점은 분명히 있다. 테스트 시간이 길어진다. MariaDB 컨테이너를 올리는 데만 몇 초가 걸린다. 그래서 `disabledWithoutDocker = true`로 로컬에서는 skip하도록 했고, CI에서만 실행되게 할 계획이다.

하지만 얻는 것도 분명하다. H2 호환 모드에 의존하던 테스트의 신뢰도 문제가 해결된다. 마이그레이션 파일을 수정하고 나서 "이게 실제로 MariaDB에서도 될까?"를 더 이상 손으로 확인할 필요가 없다.

운영에서 마이그레이션이 실패하면 롤백이 필요하고, 다운타임이 생기고, Flyway 상태를 수동으로 수습해야 한다. 그 비용을 생각하면 컨테이너 올리는 몇 초는 충분히 투자할 가치가 있다.
