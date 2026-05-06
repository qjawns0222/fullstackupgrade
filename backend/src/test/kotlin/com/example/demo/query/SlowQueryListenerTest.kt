package com.example.demo.query

import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

/**
 * For Kotlin + Mockito, we track invocations via a simple spy/fake pattern
 * to avoid null-return issues with ArgumentCaptor in "never" assertions.
 */
class FakeQueryInspector : QueryInspector {
    val slowQueryEvents = mutableListOf<SlowQueryEvent>()
    val n1Events = mutableListOf<N1QueryEvent>()

    override fun onSlowQuery(event: SlowQueryEvent) {
        slowQueryEvents.add(event)
    }

    override fun onN1Detected(event: N1QueryEvent) {
        n1Events.add(event)
    }
}

@ExtendWith(MockitoExtension::class)
class SlowQueryListenerTest {

    @AfterEach
    fun cleanup() {
        QueryExecutionContext.clear()
    }

    private fun buildExecutionInfo(elapsedMs: Long): ExecutionInfo {
        val execInfo = ExecutionInfo()
        execInfo.elapsedTime = elapsedMs
        return execInfo
    }

    private fun buildQueryInfo(sql: String): QueryInfo {
        val qi = QueryInfo()
        qi.query = sql
        return qi
    }

    @Test
    fun `onSlowQuery is called when elapsed time exceeds threshold`() {
        val inspector = FakeQueryInspector()
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 300L,
            n1ThresholdCount = 10,
            inspector = inspector
        )
        val execInfo = buildExecutionInfo(500L)

        listener.afterQuery(execInfo, mutableListOf(buildQueryInfo("SELECT * FROM users WHERE id = 1")))

        assertEquals(1, inspector.slowQueryEvents.size)
        assertEquals(500L, inspector.slowQueryEvents[0].elapsedTimeMs)
        assertEquals(300L, inspector.slowQueryEvents[0].thresholdMs)
    }

    @Test
    fun `onSlowQuery is NOT called when elapsed time is below threshold`() {
        val inspector = FakeQueryInspector()
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 300L,
            n1ThresholdCount = 10,
            inspector = inspector
        )

        listener.afterQuery(buildExecutionInfo(100L), mutableListOf(buildQueryInfo("SELECT * FROM users WHERE id = 1")))

        assertEquals(0, inspector.slowQueryEvents.size)
    }

    @Test
    fun `onN1Detected is triggered exactly once when same SQL reaches the threshold`() {
        val inspector = FakeQueryInspector()
        val n1Threshold = 3
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 9999L,
            n1ThresholdCount = n1Threshold,
            inspector = inspector
        )
        val sql = "SELECT * FROM job_applications WHERE user_id = 1"
        val execInfo = buildExecutionInfo(10L)

        repeat(n1Threshold) {
            listener.afterQuery(execInfo, mutableListOf(buildQueryInfo(sql)))
        }

        assertEquals(1, inspector.n1Events.size)
        assertEquals(n1Threshold, inspector.n1Events[0].executionCount)
    }

    @Test
    fun `onN1Detected is NOT triggered before threshold is reached`() {
        val inspector = FakeQueryInspector()
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 9999L,
            n1ThresholdCount = 5,
            inspector = inspector
        )
        val sql = "SELECT * FROM resumes WHERE id = 1"

        repeat(4) {
            listener.afterQuery(buildExecutionInfo(10L), mutableListOf(buildQueryInfo(sql)))
        }

        assertEquals(0, inspector.n1Events.size)
    }

    @Test
    fun `slow query threshold fires at exactly the boundary`() {
        val inspector = FakeQueryInspector()
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 300L,
            n1ThresholdCount = 100,
            inspector = inspector
        )

        listener.afterQuery(buildExecutionInfo(300L), mutableListOf(buildQueryInfo("SELECT 1")))

        assertEquals(1, inspector.slowQueryEvents.size)
    }

    @Test
    fun `hintRegistry records slow query hit`() {
        val inspector = FakeQueryInspector()
        val registry = QueryHintRegistry(hintThreshold = 3)
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 100L,
            n1ThresholdCount = 100,
            inspector = inspector,
            hintRegistry = registry
        )
        val sql = "SELECT * FROM resumes WHERE id = 1"

        listener.afterQuery(buildExecutionInfo(200L), mutableListOf(buildQueryInfo(sql)))

        assertEquals(1, registry.slowCount(QueryExecutionContext.normalize(sql)))
    }

    @Test
    fun `hintRegistry promotes to hint after threshold`() {
        val inspector = FakeQueryInspector()
        val registry = QueryHintRegistry(hintThreshold = 2)
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 100L,
            n1ThresholdCount = 100,
            inspector = inspector,
            hintRegistry = registry
        )
        val sql = "SELECT * FROM resumes WHERE id = 1"

        repeat(2) {
            listener.afterQuery(buildExecutionInfo(200L), mutableListOf(buildQueryInfo(sql)))
        }

        assertTrue(registry.isRegistered(QueryExecutionContext.normalize(sql)))
    }

    @Test
    fun `hintRegistry not updated for fast queries`() {
        val inspector = FakeQueryInspector()
        val registry = QueryHintRegistry(hintThreshold = 2)
        val listener = SlowQueryListener(
            slowQueryThresholdMs = 300L,
            n1ThresholdCount = 100,
            inspector = inspector,
            hintRegistry = registry
        )
        val sql = "SELECT * FROM resumes WHERE id = 1"

        repeat(5) {
            listener.afterQuery(buildExecutionInfo(50L), mutableListOf(buildQueryInfo(sql)))
        }

        assertEquals(0, registry.slowCount(QueryExecutionContext.normalize(sql)))
    }
}
