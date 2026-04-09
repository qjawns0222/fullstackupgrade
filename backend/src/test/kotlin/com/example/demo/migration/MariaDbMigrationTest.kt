package com.example.demo.migration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

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

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `모든 Flyway 마이그레이션이 실제 MariaDB에서 성공적으로 실행된다`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
            Long::class.java
        ) ?: 0L
        assertThat(count).isGreaterThanOrEqualTo(1L)
    }

    @Test
    fun `V1 users 테이블이 올바른 컬럼과 함께 생성된다`() {
        val columns = getColumnNames("users")
        assertThat(columns).containsExactlyInAnyOrder("id", "username", "password", "role", "email")
    }

    @Test
    fun `V1 resumes 테이블이 올바른 컬럼과 함께 생성된다`() {
        val columns = getColumnNames("resumes")
        assertThat(columns).containsAll(listOf("id", "original_file_name", "content", "user_id", "created_at"))
    }

    @Test
    fun `V1 job_applications 테이블이 생성된다`() {
        val columns = getColumnNames("job_applications")
        assertThat(columns).containsAll(listOf("id", "company_name", "position", "status", "user_id"))
    }

    @Test
    fun `V2 users 테이블에 MFA 컬럼이 추가된다`() {
        val columns = getColumnNames("users")
        assertThat(columns).containsAnyOf("mfa_enabled", "mfa_secret", "totp_secret")
    }

    @Test
    fun `V4 webhook_endpoints 테이블이 생성된다`() {
        val exists = tableExists("webhook_endpoints")
        assertThat(exists).isTrue()
    }

    @Test
    fun `V4 webhook_delivery_logs 테이블이 생성된다`() {
        val exists = tableExists("webhook_delivery_logs")
        assertThat(exists).isTrue()
    }

    @Test
    fun `V5 dlq_messages 테이블이 생성된다`() {
        val exists = tableExists("dlq_messages")
        assertThat(exists).isTrue()
    }

    @Test
    fun `V6 key_rotation_history 테이블이 생성된다`() {
        val exists = tableExists("key_rotation_history")
        assertThat(exists).isTrue()
    }

    @Test
    fun `V7 api_snapshots 테이블이 생성된다`() {
        val exists = tableExists("api_snapshots")
        assertThat(exists).isTrue()
    }

    @Test
    fun `V7 api_breaking_changes 테이블이 생성된다`() {
        val exists = tableExists("api_breaking_changes")
        assertThat(exists).isTrue()
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

    private fun getColumnNames(tableName: String): List<String> =
        jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """.trimIndent(),
            String::class.java,
            tableName
        )

    private fun tableExists(tableName: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """.trimIndent(),
            Long::class.java,
            tableName
        ) ?: 0L
        return count > 0L
    }
}
