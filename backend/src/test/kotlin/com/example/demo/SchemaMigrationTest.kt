package com.example.demo

import com.example.demo.config.QuerydslConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@Import(QuerydslConfig::class)
@TestPropertySource(
        properties =
                [
                        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.flyway.enabled=true",
                        "spring.flyway.baseline-on-migrate=true"]
)
class SchemaMigrationTest {

        @Autowired lateinit var jdbcTemplate: JdbcTemplate

        @Test
        fun `migration should run successfully and tables should exist`() {
                // Verify 'flyway_schema_history' exists
                val flywayCount =
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM \"flyway_schema_history\"",
                                Long::class.java
                        )
                                ?: 0L
                assertThat(flywayCount).isGreaterThanOrEqualTo(1L)

                // Verify 'users' table exists
                val userTableCount =
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'users' OR table_name = 'USERS'",
                                Long::class.java
                        )
                                ?: 0L
                assertThat(userTableCount).isGreaterThanOrEqualTo(1L)
        }
}
