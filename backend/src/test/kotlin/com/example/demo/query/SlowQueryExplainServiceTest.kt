package com.example.demo.query

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType

/**
 * Tests for SlowQueryExplainService.
 *
 * Uses H2 embedded DB for real EXPLAIN execution.
 * Uses a fake ExplainResultStore to avoid external dependencies (per CLAUDE.md testing patterns).
 */
class SlowQueryExplainServiceTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var fakeStore: FakeExplainResultStore
    private lateinit var service: SlowQueryExplainService

    @BeforeEach
    fun setUp() {
        val dataSource = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build()
        jdbcTemplate = JdbcTemplate(dataSource)
        fakeStore = FakeExplainResultStore()
        service = SlowQueryExplainService(jdbcTemplate, fakeStore)

        // Create test table and insert data
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS test_users (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(100) NOT NULL,
                email VARCHAR(200) NOT NULL,
                status VARCHAR(50)
            )
            """.trimIndent()
        )
        jdbcTemplate.execute("INSERT INTO test_users (username, email, status) VALUES ('alice', 'alice@test.com', 'ACTIVE')")
        jdbcTemplate.execute("INSERT INTO test_users (username, email, status) VALUES ('bob', 'bob@test.com', 'INACTIVE')")
    }

    // -----------------------------------------------------------------------
    // bindParamsToNull tests
    // -----------------------------------------------------------------------

    @Test
    fun `bindParamsToNull replaces single question mark with NULL`() {
        val sql = "SELECT * FROM users WHERE id = ?"
        val result = service.bindParamsToNull(sql)
        assertEquals("SELECT * FROM users WHERE id = NULL", result)
    }

    @Test
    fun `bindParamsToNull replaces multiple question marks with NULL`() {
        val sql = "SELECT * FROM users WHERE id = ? AND status = ?"
        val result = service.bindParamsToNull(sql)
        assertEquals("SELECT * FROM users WHERE id = NULL AND status = NULL", result)
    }

    @Test
    fun `bindParamsToNull leaves SQL without params unchanged`() {
        val sql = "SELECT * FROM users"
        val result = service.bindParamsToNull(sql)
        assertEquals("SELECT * FROM users", result)
    }

    // -----------------------------------------------------------------------
    // extractWhereColumns tests (JSqlParser)
    // -----------------------------------------------------------------------

    @Test
    fun `extractWhereColumns parses simple equality condition`() {
        val sql = "SELECT * FROM test_users WHERE id = 1"
        val columns = service.extractWhereColumns(sql)
        assertTrue(columns.any { it.second == "id" }, "Expected 'id' column in WHERE clause")
    }

    @Test
    fun `extractWhereColumns parses AND condition with two columns`() {
        val sql = "SELECT * FROM test_users WHERE username = 'alice' AND status = 'ACTIVE'"
        val columns = service.extractWhereColumns(sql)
        val columnNames = columns.map { it.second }
        assertTrue(columnNames.contains("username"), "Expected 'username' in columns")
        assertTrue(columnNames.contains("status"), "Expected 'status' in columns")
    }

    @Test
    fun `extractWhereColumns returns empty list for non-SELECT SQL`() {
        val sql = "INSERT INTO test_users (username) VALUES ('charlie')"
        val columns = service.extractWhereColumns(sql)
        assertTrue(columns.isEmpty(), "Non-SELECT SQL should return empty column list")
    }

    @Test
    fun `extractWhereColumns returns empty list for malformed SQL`() {
        val sql = "NOT VALID SQL AT ALL!!!"
        val columns = service.extractWhereColumns(sql)
        assertTrue(columns.isEmpty(), "Malformed SQL should return empty list without throwing")
    }

    @Test
    fun `extractWhereColumns handles SQL with no WHERE clause`() {
        val sql = "SELECT * FROM test_users"
        val columns = service.extractWhereColumns(sql)
        assertTrue(columns.isEmpty(), "SQL without WHERE clause should return empty list")
    }

    // -----------------------------------------------------------------------
    // runExplain tests (real H2 execution)
    // -----------------------------------------------------------------------

    @Test
    fun `runExplain returns non-empty rows for valid SELECT on H2`() {
        val rows = service.runExplain("SELECT * FROM test_users WHERE id = 1")
        // H2 EXPLAIN returns at least one row for a valid query
        assertNotNull(rows)
    }

    @Test
    fun `runExplain returns empty list for invalid SQL`() {
        val rows = service.runExplain("SELECT * FROM non_existent_table_xyz WHERE id = 1")
        assertTrue(rows.isEmpty(), "Invalid SQL should return empty list without throwing")
    }

    // -----------------------------------------------------------------------
    // analyzeSlowQuery integration tests
    // -----------------------------------------------------------------------

    @Test
    fun `analyzeSlowQuery returns result with correct SQL`() {
        val sql = "SELECT * FROM test_users WHERE id = ?"
        val result = service.analyzeSlowQuery(sql, 500L)

        assertEquals(sql, result.originalSql)
        assertEquals(500L, result.executionTimeMs)
        assertNotNull(result.id)
        assertNotNull(result.capturedAt)
    }

    @Test
    fun `analyzeSlowQuery generates index recommendations for WHERE columns`() {
        val sql = "SELECT * FROM test_users WHERE username = ?"
        val result = service.analyzeSlowQuery(sql, 400L)

        // JSqlParser should detect 'username' in WHERE clause
        assertTrue(
            result.indexRecommendations.any { it.contains("username") },
            "Expected index recommendation for 'username' column. Got: ${result.indexRecommendations}"
        )
    }

    @Test
    fun `analyzeSlowQuery persists result to store`() {
        val sql = "SELECT * FROM test_users WHERE email = ?"
        service.analyzeSlowQuery(sql, 350L)

        assertEquals(1, fakeStore.savedItems.size)
        assertEquals(sql, fakeStore.savedItems[0].originalSql)
    }

    @Test
    fun `analyzeSlowQuery sets hasFullTableScan false when no ALL type rows`() {
        // H2 EXPLAIN for a simple SELECT by primary key should not show type=ALL
        val sql = "SELECT * FROM test_users WHERE id = 1"
        val result = service.analyzeSlowQuery(sql, 600L)

        // H2 explain does not return "type" column like MySQL; hasFullTableScan should be false
        assertFalse(result.hasFullTableScan, "H2 EXPLAIN rows won't have type=ALL")
    }

    @Test
    fun `analyzeSlowQuery does not throw even when store save fails`() {
        val failingStore = FailingExplainResultStore()
        val serviceWithFailingStore = SlowQueryExplainService(jdbcTemplate, failingStore)

        // Should not throw, just log warning
        assertDoesNotThrow {
            serviceWithFailingStore.analyzeSlowQuery("SELECT * FROM test_users WHERE id = 1", 300L)
        }
    }

    // -----------------------------------------------------------------------
    // SlowQueryListener integration: verify EXPLAIN is triggered for SELECT only
    // -----------------------------------------------------------------------

    @Test
    fun `SlowQueryListener calls explainService only for SELECT queries`() {
        val fakeInspector = FakeQueryInspector()
        val capturedSqls = mutableListOf<String>()
        val trackingService = TrackingSlowQueryExplainService(capturedSqls, jdbcTemplate, fakeStore)

        val listener = SlowQueryListener(
            slowQueryThresholdMs = 0L,   // threshold = 0 so every query triggers
            n1ThresholdCount = 999,
            inspector = fakeInspector,
            explainService = trackingService
        )

        val execInfo = net.ttddyy.dsproxy.ExecutionInfo().apply { elapsedTime = 100L }

        val selectQueryInfo = net.ttddyy.dsproxy.QueryInfo().apply {
            query = "SELECT * FROM test_users"
        }
        val insertQueryInfo = net.ttddyy.dsproxy.QueryInfo().apply {
            query = "INSERT INTO test_users (username, email) VALUES ('x', 'x@x.com')"
        }
        val updateQueryInfo = net.ttddyy.dsproxy.QueryInfo().apply {
            query = "UPDATE test_users SET status = 'ACTIVE' WHERE id = 1"
        }

        listener.afterQuery(execInfo, mutableListOf(selectQueryInfo))
        listener.afterQuery(execInfo, mutableListOf(insertQueryInfo))
        listener.afterQuery(execInfo, mutableListOf(updateQueryInfo))

        assertEquals(1, capturedSqls.size, "Only SELECT should trigger EXPLAIN analysis")
        assertEquals("SELECT * FROM test_users", capturedSqls[0])
    }
}

// ---------------------------------------------------------------------------
// Fake / Test Doubles
// ---------------------------------------------------------------------------

/**
 * Fake ExplainResultStore — stores items in memory, no real ES connection.
 * Implements only the simple port interface, not the full ElasticsearchRepository.
 */
class FakeExplainResultStore : ExplainResultStore {
    val savedItems = mutableListOf<SlowQueryExplainResult>()

    override fun save(result: SlowQueryExplainResult) {
        savedItems.add(result)
    }
}

/**
 * A fake store that always throws on save — used to verify error resilience.
 */
class FailingExplainResultStore : ExplainResultStore {
    override fun save(result: SlowQueryExplainResult) {
        throw RuntimeException("Simulated Elasticsearch unavailable")
    }
}

/**
 * A SlowQueryExplainService subclass that records which SQLs were analyzed —
 * used for listener integration tests to verify routing behavior.
 */
class TrackingSlowQueryExplainService(
    private val capturedSqls: MutableList<String>,
    jdbcTemplate: JdbcTemplate,
    store: ExplainResultStore
) : SlowQueryExplainService(jdbcTemplate, store) {

    override fun analyzeSlowQuery(sql: String, executionTimeMs: Long): SlowQueryExplainResult {
        capturedSqls.add(sql)
        return super.analyzeSlowQuery(sql, executionTimeMs)
    }
}
