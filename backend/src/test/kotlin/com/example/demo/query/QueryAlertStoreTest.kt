package com.example.demo.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class QueryAlertStoreTest {

    private lateinit var store: QueryAlertStore

    @BeforeEach
    fun setUp() {
        store = QueryAlertStore()
    }

    @Test
    fun `onSlowQuery stores event and is retrievable`() {
        val event = SlowQueryEvent(
            sql = "SELECT * FROM users",
            elapsedTimeMs = 400L,
            thresholdMs = 300L,
            callerClass = "UserService",
            callerMethod = "findAll",
            detectedAt = LocalDateTime.now()
        )

        store.onSlowQuery(event)

        assertEquals(1, store.getTotalSlowQueryCount())
        assertEquals(1, store.getRecentSlowQueries().size)
        assertEquals(400L, store.getRecentSlowQueries().first().elapsedTimeMs)
    }

    @Test
    fun `onN1Detected stores event and is retrievable`() {
        val event = N1QueryEvent(
            normalizedSql = "SELECT * FROM resumes WHERE id = ?",
            executionCount = 5,
            thresholdCount = 5,
            callerClass = "ResumeService",
            callerMethod = "findAll",
            detectedAt = LocalDateTime.now()
        )

        store.onN1Detected(event)

        assertEquals(1, store.getTotalN1Count())
        assertEquals(1, store.getRecentN1Alerts().size)
        assertEquals(5, store.getRecentN1Alerts().first().executionCount)
    }

    @Test
    fun `getRecentSlowQueries returns at most 20 events`() {
        repeat(30) { i ->
            store.onSlowQuery(
                SlowQueryEvent(
                    sql = "SELECT $i",
                    elapsedTimeMs = 400L,
                    thresholdMs = 300L,
                    callerClass = "X",
                    callerMethod = "y"
                )
            )
        }
        // total count can be up to 100 but getRecent caps at 20
        assertTrue(store.getRecentSlowQueries().size <= 20)
    }

    @Test
    fun `store starts empty`() {
        assertEquals(0, store.getTotalSlowQueryCount())
        assertEquals(0, store.getTotalN1Count())
        assertTrue(store.getRecentSlowQueries().isEmpty())
        assertTrue(store.getRecentN1Alerts().isEmpty())
    }
}
